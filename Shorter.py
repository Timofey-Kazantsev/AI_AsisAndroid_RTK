import torch
from transformers import AutoTokenizer, AutoModelForCausalLM

# Конфигурация
INPUT_FILE = "speakers_phrases.txt"  # Файл с текстом
OUTPUT_FILE = "summary.txt"  # Файл для пересказа
MODEL_PATH = "D:\Llama"  # Укажите путь к папке с файлами модели


def load_model():
    """
    Загружает модель OpenLLaMA и токенизатор из локальной папки.
    """
    print(f"Загрузка модели из {MODEL_PATH}...")
    try:
        model = AutoModelForCausalLM.from_pretrained(MODEL_PATH, torch_dtype=torch.float16)
        tokenizer = AutoTokenizer.from_pretrained(MODEL_PATH)
    except Exception as e:
        print(f"Ошибка загрузки модели: {e}")
        print("Убедитесь, что все файлы модели (config.json, pytorch_model.bin, tokenizer.model) находятся в папке.")
        raise

    # Переносим модель на GPU, если доступно
    if torch.cuda.is_available():
        model = model.to("cuda")
        print("Модель загружена на GPU.")
    else:
        print("Модель загружена на CPU.")

    return model, tokenizer


def read_input_file(file_path):
    """
    Читает весь текст из файла.
    """
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            text = f.read().strip()
        return text
    except FileNotFoundError:
        print(f"Файл {file_path} не найден.")
        return ""
    except Exception as e:
        print(f"Ошибка при чтении файла {file_path}: {str(e)}")
        return ""


def generate_summary(text, model, tokenizer):
    """
    Генерирует краткий пересказ текста с помощью OpenLLaMA.
    """
    if not text:
        return "Пересказ невозможен: файл пуст или не найден."

    # Формируем промпт
    prompt = (
        "Сделай краткий пересказ следующего текста на русском языке:\n\n"
        f"{text}\n\n"
        "Перескажи основные моменты в 2-3 предложениях."
    )

    # Токенизируем промпт
    inputs = tokenizer(prompt, return_tensors="pt", max_length=1024, truncation=True)

    # Переносим входные данные на устройство модели
    if torch.cuda.is_available():
        inputs = {key: value.to("cuda") for key, value in inputs.items()}

    # Генерируем пересказ
    summary_ids = model.generate(
        **inputs,
        max_length=300,  # Длина для краткого пересказа
        min_length=50,  # Минимум для 2-3 предложений
        do_sample=True,  # Выборка для разнообразия
        top_k=50,  # Ограничение выборки
        top_p=0.95,  # Вероятностная фильтрация
        temperature=0.7,  # Контроль креативности
        num_beams=1,  # Без beam search для скорости
        early_stopping=True
    )

    # Декодируем результат
    summary = tokenizer.decode(summary_ids[0], skip_special_tokens=True)

    # Убираем промпт из вывода
    summary = summary.replace(prompt, "").strip()
    return summary


def save_summary(summary, output_file):
    """
    Сохраняет пересказ в файл.
    """
    with open(output_file, "w", encoding="utf-8") as f:
        f.write("Краткий пересказ:\n\n")
        f.write(summary)
    print(f"Пересказ сохранён в файл: {output_file}")


def main():
    print(f"Чтение файла: {INPUT_FILE}")

    # Загружаем модель и токенизатор
    model, tokenizer = load_model()

    # Читаем весь текст из файла
    input_text = read_input_file(INPUT_FILE)

    if not input_text:
        print("Не удалось обработать файл.")
        return

    # Генерируем пересказ
    summary = generate_summary(input_text, model, tokenizer)
    save_summary(summary, OUTPUT_FILE)


if __name__ == "__main__":
    main()