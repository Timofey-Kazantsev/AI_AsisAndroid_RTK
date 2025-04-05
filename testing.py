import os
from datetime import timedelta
from collections import defaultdict
import warnings
import torch
import torchaudio
from dotenv import load_dotenv
from pydub import AudioSegment
import subprocess
import whisper
import tempfile
import numpy as np
from pyannote.audio import Pipeline
from pyannote.audio import Model
from omegaconf import ListConfig  # Для ListConfig
from omegaconf.base import ContainerMetadata  # Для ContainerMetadata

# Добавляем ListConfig и ContainerMetadata в список безопасных глобальных объектов
torch.serialization.add_safe_globals([ListConfig, ContainerMetadata])

# Конфигурация
INPUT_FILE = "123.ogg"
SAMPLE_RATE = 16000
WHISPER_MODEL = "small"
MAX_GAP_FOR_MERGE = 0.1  # Максимальный разрыв для объединения сегментов (в секундах)
SIMILARITY_THRESHOLD = 0.75  # Порог схожести для идентификации спикера
load_dotenv()
AUTH_TOKEN = os.environ.get('AUTH_TOKEN') # Ваш токен

warnings.filterwarnings("ignore")


class SpeakerRecognizer:
    def __init__(self):
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        print(f"Используется устройство: {self.device}")

        # Загрузка pipeline для диаризации из pyannote
        print("Загрузка модели pyannote.audio...")
        self.pipeline = Pipeline.from_pretrained(
            "pyannote/speaker-diarization",
            use_auth_token=AUTH_TOKEN
        ).to(self.device)

        self.pipeline.instantiate({
            "clustering": {
                "method": "centroid",
                "threshold": 0.7,  # Порог кластеризации
            }
        })

        # Загрузка модели для извлечения эмбеддингов
        print("Загрузка модели эмбеддингов...")
        self.embedding_model = Model.from_pretrained(
            "pyannote/embedding",
            use_auth_token=AUTH_TOKEN
        ).to(self.device)

        print("Загрузка модели Whisper...")
        self.whisper_model = whisper.load_model(WHISPER_MODEL, device=self.device)

        # Словарь для хранения эмбеддингов спикеров
        self.speaker_embeddings = {}
        self.next_speaker_id = 1

    def recognize_speech(self, audio_segment):
        try:
            temp_dir = tempfile.mkdtemp()
            temp_path = os.path.join(temp_dir, "temp_audio.wav")
            audio_segment.export(temp_path, format="wav")
            result = self.whisper_model.transcribe(
                temp_path,
                language="ru",
                fp16=torch.cuda.is_available(),
                beam_size=5
            )
            os.remove(temp_path)
            os.rmdir(temp_dir)
            return result["text"].strip()
        except Exception as e:
            print(f"Ошибка распознавания: {str(e)}")
            return ""

    def get_embedding(self, audio_segment):
        try:
            temp_dir = tempfile.mkdtemp()
            temp_path = os.path.join(temp_dir, "temp_audio.wav")
            audio_segment.export(temp_path, format="wav")
            waveform, sr = torchaudio.load(temp_path)
            print(f"Waveform shape: {waveform.shape}, Sample rate: {sr}")
            if sr != SAMPLE_RATE:
                waveform = torchaudio.functional.resample(waveform, sr, SAMPLE_RATE)
            waveform = waveform.to(self.device)
            if waveform.shape[0] > 1:
                waveform = torch.mean(waveform, dim=0, keepdim=True)
            print(f"Processed waveform shape: {waveform.shape}")
            with torch.no_grad():
                embedding = self.embedding_model({"waveform": waveform.unsqueeze(0), "sample_rate": SAMPLE_RATE})
            embedding_np = embedding.cpu().numpy()
            os.remove(temp_path)
            os.rmdir(temp_dir)
            return embedding_np
        except Exception as e:
            print(f"Ошибка получения эмбеддинга: {str(e)}")
            return None

    def cosine_similarity(self, emb1, emb2):
        if emb1 is None or emb2 is None:
            return 0.0
        emb1 = emb1.flatten()
        emb2 = emb2.flatten()
        return np.dot(emb1, emb2) / (np.linalg.norm(emb1) * np.linalg.norm(emb2))

    def identify_speaker(self, embedding):
        if embedding is None:
            spk_id = f"SPEAKER_{self.next_speaker_id}"
            self.next_speaker_id += 1
            return spk_id

        for spk_id, emb_list in self.speaker_embeddings.items():
            avg_embedding = np.mean(emb_list, axis=0)
            similarity = self.cosine_similarity(embedding, avg_embedding)
            print(f"Сравнение с {spk_id}: сходство = {similarity:.3f}")
            if similarity > SIMILARITY_THRESHOLD:
                self.speaker_embeddings[spk_id].append(embedding)
                return spk_id

        spk_id = f"SPEAKER_{self.next_speaker_id}"
        self.speaker_embeddings[spk_id] = [embedding]
        self.next_speaker_id += 1
        print(f"Новый спикер: {spk_id}")
        return spk_id


def load_audio_with_ffmpeg(voice_file, ffmpeg_path="ffmpeg"):
    process = subprocess.Popen(
        [
            ffmpeg_path,
            "-loglevel", "quiet",
            "-i", voice_file,
            "-ar", str(SAMPLE_RATE),
            "-ac", "1",
            "-f", "s16le",
            "-"
        ],
        stdout=subprocess.PIPE
    )
    raw_audio = process.communicate()[0]
    audio_segment = AudioSegment(
        data=raw_audio,
        sample_width=2,
        frame_rate=SAMPLE_RATE,
        channels=1
    )
    return audio_segment


def process_audio(file_path, recognizer):
    try:
        audio = load_audio_with_ffmpeg(file_path).normalize()
        temp_dir = tempfile.mkdtemp()
        temp_wav = os.path.join(temp_dir, "temp.wav")
        audio.export(temp_wav, format="wav")

        diarization = recognizer.pipeline(temp_wav)
        results = []
        speaker_texts = defaultdict(list)
        speaker_map = {}

        raw_segments = []
        for turn, _, speaker in diarization.itertracks(yield_label=True):
            if speaker not in speaker_map:
                speaker_map[speaker] = f"TEMP_{len(speaker_map) + 1}"
            start = turn.start
            end = turn.end
            duration = end - start
            spk_id = speaker_map[speaker]
            raw_segments.append({
                "speaker": spk_id,
                "start": start,
                "end": end,
                "duration": duration
            })

        if raw_segments:
            current = raw_segments[0]
            for next_seg in raw_segments[1:]:
                time_gap = next_seg["start"] - current["end"]
                if current["speaker"] == next_seg["speaker"] and time_gap <= MAX_GAP_FOR_MERGE:
                    current["end"] = next_seg["end"]
                    current["duration"] = current["end"] - current["start"]
                else:
                    seg = audio[int(current["start"] * 1000):int(current["end"] * 1000)]
                    embedding = recognizer.get_embedding(seg)
                    spk_id = recognizer.identify_speaker(embedding)
                    current["speaker"] = spk_id
                    text = recognizer.recognize_speech(seg)
                    current["text"] = text
                    results.append(current)
                    if text:
                        speaker_texts[spk_id].append({"start": current["start"], "text": text})
                    current = next_seg

            seg = audio[int(current["start"] * 1000):int(current["end"] * 1000)]
            embedding = recognizer.get_embedding(seg)
            spk_id = recognizer.identify_speaker(embedding)
            current["speaker"] = spk_id
            text = recognizer.recognize_speech(seg)
            current["text"] = text
            results.append(current)
            if text:
                speaker_texts[spk_id].append({"start": current["start"], "text": text})

        os.remove(temp_wav)
        os.rmdir(temp_dir)
        return results, speaker_texts

    except Exception as e:
        print(f"Ошибка обработки аудио: {str(e)}")
        return [], {}


def print_results(results, speaker_texts):
    if not results:
        print("Нет результатов для отображения")
        return

    print("\nРезультаты диаризации:")
    speaker_stats = defaultdict(float)
    for i, seg in enumerate(results, 1):
        speaker_stats[seg["speaker"]] += seg["duration"]
        print(f"{i:3d}. {seg['speaker']:12s}: {timedelta(seconds=int(seg['start']))} - {timedelta(seconds=int(seg['end']))} ({seg['duration']:.1f} сек)")

    print("\nОбщее время речи по спикерам:")
    for speaker, duration in sorted(speaker_stats.items(), key=lambda x: x[1], reverse=True):
        print(f"- {speaker:12s}: {timedelta(seconds=int(duration))}")

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
    else:
        print("Не удалось обнаружить речевые сегменты")


if __name__ == "__main__":
    main()