import os
from datetime import timedelta
from collections import defaultdict
import warnings
import torch
import torchaudio
import tempfile
import numpy as np
from speechbrain.pretrained import SpeakerRecognition
from whisper import load_model
from audio_conv import load_audio_with_ffmpeg

# Конфигурация
INPUT_FILE = "video.mp4"
OUTPUT_FILE_BY_SPEAKER = "speakers_phrases.txt"  # Первый файл: по спикерам
OUTPUT_FILE_BY_TIME = "timeline_phrases.txt"    # Второй файл: по времени
SAMPLE_RATE = 16000
BASE_SIMILARITY_THRESHOLD = 0.35
MERGE_SIMILARITY_THRESHOLD = 0.5
MIN_SEGMENT_LENGTH = 1.0
MERGE_THRESHOLD = 0.5
CONTEXT_WINDOW = 5.0
WHISPER_MODEL_NAME = "small"
warnings.filterwarnings("ignore")


class SpeakerRecognizer:
    def __init__(self):
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        print(f"Используется устройство: {self.device}")

        print("Загрузка модели SpeechBrain...")
        self.embedding_model = SpeakerRecognition.from_hparams(source="speechbrain/spkrec-ecapa-voxceleb")

        print("Загрузка модели Whisper...")
        self.whisper_model = load_model(WHISPER_MODEL_NAME)

        self.speaker_embeddings = {}
        self.last_speaker_segment = {}
        self.next_speaker_id = 1

    def recognize_speech_with_timestamps(self, audio_segment):
        try:
            temp_dir = tempfile.mkdtemp()
            temp_path = os.path.join(temp_dir, "temp_audio.wav")
            audio_segment.export(temp_path, format="wav")
            result = self.whisper_model.transcribe(temp_path, language="ru", word_timestamps=True)
            os.remove(temp_path)
            os.rmdir(temp_dir)
            return result["segments"]
        except Exception as e:
            print(f"Ошибка распознавания с временными метками: {str(e)}")
            return []

    def preprocess_waveform(self, waveform, min_length=16000):
        if waveform.ndim == 2:
            waveform = waveform.squeeze(0)

        waveform_length = waveform.shape[-1]
        target_length = max(waveform_length, min_length)
        if waveform_length < target_length:
            padding_length = target_length - waveform_length
            waveform = torch.nn.functional.pad(waveform, (0, padding_length), mode='constant', value=0)

        waveform = waveform / (torch.max(torch.abs(waveform)) + 1e-8)
        return waveform

    def get_embedding(self, audio_segment):
        try:
            temp_dir = tempfile.mkdtemp()
            temp_path = os.path.join(temp_dir, "temp_audio.wav")
            audio_segment.export(temp_path, format="wav")

            waveform, sr = torchaudio.load(temp_path)
            os.remove(temp_path)
            os.rmdir(temp_dir)

            if sr != SAMPLE_RATE:
                waveform = torchaudio.functional.resample(waveform, sr, SAMPLE_RATE)

            waveform = self.preprocess_waveform(waveform, min_length=int(SAMPLE_RATE * MIN_SEGMENT_LENGTH))
            waveform = waveform.unsqueeze(0)
            print(f"Prepared waveform shape for embedding: {waveform.shape}")

            with torch.no_grad():
                embedding = self.embedding_model.encode_batch(waveform)
                return embedding.squeeze(0).cpu().numpy()

        except Exception as e:
            print(f"Ошибка получения эмбеддинга: {str(e)}")
            return None

    def cosine_similarity(self, emb1, emb2):
        if emb1 is None or emb2 is None:
            return 0.0
        if emb1.ndim > 1:
            emb1 = emb1.ravel()
        if emb2.ndim > 1:
            emb2 = emb2.ravel()
        return np.dot(emb1, emb2) / (np.linalg.norm(emb1) * np.linalg.norm(emb2))

    def identify_speaker(self, embedding, segment_duration, text, start_time):
        threshold = BASE_SIMILARITY_THRESHOLD if segment_duration >= 1.0 else 0.25

        if embedding is None:
            spk_id = f"SPEAKER_{self.next_speaker_id}"
            self.next_speaker_id += 1
            self.speaker_embeddings[spk_id] = []
            self.last_speaker_segment[spk_id] = start_time
            print(f"Создан новый спикер (нет эмбеддинга): {spk_id} (длительность: {segment_duration:.2f} сек, текст: '{text}')")
            return spk_id

        if not self.speaker_embeddings:
            spk_id = f"SPEAKER_{self.next_speaker_id}"
            self.next_speaker_id += 1
            self.speaker_embeddings[spk_id] = [embedding]
            self.last_speaker_segment[spk_id] = start_time
            print(f"Создан первый спикер: {spk_id} (длительность: {segment_duration:.2f} сек, текст: '{text}')")
            return spk_id

        best_score = -1
        best_speaker = None

        for spk_id, emb_list in self.speaker_embeddings.items():
            if not emb_list:
                continue
            avg_embedding = np.mean(emb_list, axis=0)
            similarity = self.cosine_similarity(embedding, avg_embedding)
            time_diff = start_time - self.last_speaker_segment.get(spk_id, float('inf'))
            print(f"Сравнение с {spk_id}: сходство = {similarity:.3f}, разница времени = {time_diff:.2f} сек (длительность: {segment_duration:.2f} сек, текст: '{text}')")

            time_bonus = 0
            if time_diff <= CONTEXT_WINDOW and time_diff >= 0:
                time_weight = 0.05 if (segment_duration < 1.0 and len(emb_list) < 2) else (0.1 if len(emb_list) >= 3 else 0.2)
                time_bonus = time_weight * (1 - time_diff / CONTEXT_WINDOW)
            stability_bonus = min(0.15, len(emb_list) * 0.02)
            score = similarity + time_bonus + stability_bonus

            print(f"Скор для {spk_id}: сходство = {similarity:.3f}, бонус времени = {time_bonus:.3f}, бонус устойчивости = {stability_bonus:.3f}, итог = {score:.3f}")

            if score > best_score:
                best_score = score
                best_speaker = spk_id

        if best_score >= threshold:
            print(f"Спикер идентифицирован как: {best_speaker} (скор: {best_score:.3f}, длительность: {segment_duration:.2f} сек, текст: '{text}')")
        else:
            spk_id = f"SPEAKER_{self.next_speaker_id}"
            self.next_speaker_id += 1
            self.speaker_embeddings[spk_id] = [embedding]
            self.last_speaker_segment[spk_id] = start_time
            print(f"Создан новый спикер: {spk_id} (скор с лучшим: {best_score:.3f}, длительность: {segment_duration:.2f} сек, текст: '{text}')")
            return spk_id

        self.speaker_embeddings[best_speaker].append(embedding)
        self.last_speaker_segment[best_speaker] = start_time
        return best_speaker


def merge_short_segments(audio, segments, recognizer):
    merged_segments = []
    current_segment = None

    for segment in segments:
        duration = segment["end"] - segment["start"]
        start_ms = segment["start"] * 1000
        end_ms = segment["end"] * 1000
        text = segment["text"].strip()

        if current_segment is None:
            current_segment = {
                "start": segment["start"],
                "end": segment["end"],
                "text": text,
                "embedding": recognizer.get_embedding(audio[int(start_ms):int(end_ms)])
            }
        else:
            seg_audio = audio[int(start_ms):int(end_ms)]
            embedding = recognizer.get_embedding(seg_audio)
            similarity = recognizer.cosine_similarity(current_segment["embedding"], embedding)
            print(f"Проверка объединения: сходство = {similarity:.3f}, текущий текст: '{text}', длительность: {duration:.2f} сек")

            if duration < MERGE_THRESHOLD and similarity >= MERGE_SIMILARITY_THRESHOLD:
                current_segment["end"] = segment["end"]
                current_segment["text"] += " " + text
                print(f"Объединено с предыдущим: новый текст: '{current_segment['text']}'")
            else:
                merged_segments.append(current_segment)
                current_segment = {
                    "start": segment["start"],
                    "end": segment["end"],
                    "text": text,
                    "embedding": embedding
                }

    if current_segment:
        merged_segments.append(current_segment)

    for seg in merged_segments:
        del seg["embedding"]

    return merged_segments


def process_audio(file_path, recognizer):
    try:
        audio = load_audio_with_ffmpeg(file_path, SAMPLE_RATE, channels=1).normalize()
        print("Выполняется распознавание речи с временными метками...")
        segments = recognizer.recognize_speech_with_timestamps(audio)

        segments = merge_short_segments(audio, segments, recognizer)

        results = []
        speaker_texts = defaultdict(list)

        for segment in segments:
            start = segment["start"] * 1000
            end = segment["end"] * 1000
            text = segment["text"].strip()
            duration = (end - start) / 1000

            if not text or duration < 0.2:
                continue

            seg_audio = audio[int(start):int(end)]
            embedding = recognizer.get_embedding(seg_audio)
            spk_id = recognizer.identify_speaker(embedding, duration, text, segment["start"])

            results.append({
                "speaker": spk_id,
                "start": start / 1000,
                "end": end / 1000,
                "text": text,
                "duration": duration
            })
            if text:
                speaker_texts[spk_id].append({"start": start / 1000, "text": text})

        return results, speaker_texts

    except Exception as e:
        print(f"Ошибка обработки аудио: {str(e)}")
        return [], {}


def save_by_speaker(speaker_texts, output_file):
    with open(output_file, "w", encoding="utf-8") as f:
        f.write("Распознанный текст по спикерам:\n")
        for speaker, texts in sorted(speaker_texts.items()):
            f.write(f"\n{speaker}:\n")
            for entry in texts:
                f.write(f"[{timedelta(seconds=int(entry['start']))}] {entry['text']}\n")
    print(f"Результаты сохранены в файл (по спикерам): {output_file}")


def save_by_time(results, output_file):
    with open(output_file, "w", encoding="utf-8") as f:
        f.write("Распознанный текст по времени:\n\n")
        # Сортируем результаты по времени начала
        sorted_results = sorted(results, key=lambda x: x["start"])
        for seg in sorted_results:
            f.write(f"[{timedelta(seconds=int(seg['start']))}] {seg['speaker']}: {seg['text']}\n")
    print(f"Результаты сохранены в файл (по времени): {output_file}")


def print_results(results, speaker_texts):
    if not results:
        print("Нет результатов для отображения")
        return

    print("\nРезультаты диаризации по предложениям:")
    for seg in results:
        print(f"{seg['speaker']}: {timedelta(seconds=int(seg['start']))} - {timedelta(seconds=int(seg['end']))} "
              f"({seg['text']}) [длительность: {seg['duration']:.2f} сек]")

    print("\nРаспознанный текст по спикерам:")
    for speaker, texts in sorted(speaker_texts.items()):
        print(f"\n{speaker}:")
        for entry in texts:
            print(f"[{timedelta(seconds=int(entry['start']))}] {entry['text']}")


def main():
    print("Инициализация системы распознавания голосов...")
    recognizer = SpeakerRecognizer()
    print(f"\nОбработка файла: {INPUT_FILE}")
    results, speaker_texts = process_audio(INPUT_FILE, recognizer)
    if results:
        print_results(results, speaker_texts)
        save_by_speaker(speaker_texts, OUTPUT_FILE_BY_SPEAKER)
        save_by_time(results, OUTPUT_FILE_BY_TIME)
    else:
        print("Не удалось обнаружить речевые сегменты")


if __name__ == "__main__":
    main()