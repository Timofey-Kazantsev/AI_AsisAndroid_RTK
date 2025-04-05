import subprocess
from pydub import AudioSegment


def load_audio_with_ffmpeg(input_file, sample_rate=16000, channels=1, ffmpeg_path="ffmpeg"):
    """
    Конвертирует аудиофайл или извлекает аудиодорожку из видеофайла в WAV с помощью ffmpeg.

    :param input_file: путь к входному файлу (аудио или видео)
    :param sample_rate: целевая частота дискретизации (по умолчанию 16000)
    :param channels: количество каналов (по умолчанию 1 - моно)
    :param ffmpeg_path: путь к исполняемому файлу ffmpeg (по умолчанию "ffmpeg")
    :return: объект AudioSegment
    :raises: Exception если конвертация не удалась
    """
    try:
        # Команда для извлечения аудио из файла (видео или аудио)
        process = subprocess.Popen(
            [
                ffmpeg_path,
                "-loglevel", "quiet",
                "-i", input_file,  # Входной файл (аудио или видео)
                "-vn",  # Отключаем видео (-vn = no video)
                "-ar", str(sample_rate),  # Частота дискретизации
                "-ac", str(channels),  # Количество каналов
                "-f", "s16le",  # Формат PCM 16-bit little-endian
                "-"  # Вывод в stdout
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE
        )
        raw_audio, error = process.communicate()

        if process.returncode != 0:
            raise Exception(f"Ошибка ffmpeg: {error.decode('utf-8')}")
        if not raw_audio:
            raise Exception("Пустой вывод от ffmpeg - файл не содержит аудиодорожки или не обработан")

        # Создаём объект AudioSegment из сырого PCM
        audio_segment = AudioSegment(
            data=raw_audio,
            sample_width=2,  # 16-bit PCM
            frame_rate=sample_rate,
            channels=channels
        )
        print(f"Конвертация успешна: {input_file}, {sample_rate} Гц, {channels} канал(ов)")
        return audio_segment

    except FileNotFoundError:
        raise Exception(f"ffmpeg не найден по пути: {ffmpeg_path}")
    except Exception as e:
        raise Exception(f"Ошибка при конвертации {input_file}: {str(e)}")