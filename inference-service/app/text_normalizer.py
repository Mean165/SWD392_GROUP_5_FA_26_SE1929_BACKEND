from __future__ import annotations

import re
import unicodedata
from decimal import Decimal, InvalidOperation
from pathlib import Path

import json
from num2words import num2words


DATE_RE = re.compile(r"(?<!\d)(\d{4})-(\d{1,2})-(\d{1,2})(?!\d)")
NUMBER_UNIT_RE = re.compile(r"(?<![\w])([+-]?\d+(?:[.,]\d+)?)(\s?%|\s?kg\b|\s?km\b|\s?cm\b|\s?mm\b|\s?m\b|\s?°c\b)?", re.IGNORECASE)
SPACE_RE = re.compile(r"\s+")

UNIT_WORDS = {
    "vi": {"%": "phần trăm", "kg": "ki-lô-gam", "km": "ki-lô-mét", "cm": "xen-ti-mét", "mm": "mi-li-mét", "m": "mét", "°c": "độ C"},
    "en": {"%": "percent", "kg": "kilograms", "km": "kilometers", "cm": "centimeters", "mm": "millimeters", "m": "meters", "°c": "degrees Celsius"},
}


class SpeechTextNormalizer:
    def __init__(self, lexicon_path: Path | None = None):
        self.lexicon: dict[str, dict[str, str]] = {"vi": {}, "en": {}}
        if lexicon_path and lexicon_path.is_file():
            data = json.loads(lexicon_path.read_text(encoding="utf-8"))
            if isinstance(data, dict):
                for language in ("vi", "en"):
                    entries = data.get(language, data)
                    if isinstance(entries, dict):
                        self.lexicon[language] = {str(k).casefold(): str(v) for k, v in entries.items()}

    def normalize(self, text: str, language: str, enabled: bool = True) -> str:
        value = unicodedata.normalize("NFC", text or "")
        value = SPACE_RE.sub(" ", value).strip()
        if not enabled:
            return value
        lang = "vi" if language.lower().startswith("vi") else "en"
        value = DATE_RE.sub(lambda match: self._date(match, lang), value)
        value = NUMBER_UNIT_RE.sub(lambda match: self._number_unit(match, lang), value)
        for source in sorted(self.lexicon[lang], key=len, reverse=True):
            value = re.sub(rf"(?<!\w){re.escape(source)}(?!\w)", self.lexicon[lang][source], value, flags=re.IGNORECASE)
        return SPACE_RE.sub(" ", value).strip()

    def _date(self, match: re.Match[str], language: str) -> str:
        year, month, day = (int(match.group(i)) for i in range(1, 4))
        if language == "vi":
            return f"ngày {num2words(day, lang='vi')} tháng {num2words(month, lang='vi')} năm {num2words(year, lang='vi')}"
        return f"{num2words(month, lang='en')} {num2words(day, lang='en')}, {num2words(year, lang='en')}"

    def _number_unit(self, match: re.Match[str], language: str) -> str:
        raw = match.group(1).replace(",", ".")
        unit = (match.group(2) or "").strip().casefold()
        try:
            number = Decimal(raw)
        except InvalidOperation:
            return match.group(0)
        if number.is_zero():
            spoken = num2words(0, lang=language)
        elif number == number.to_integral_value():
            spoken = num2words(int(number), lang=language)
        else:
            whole, fraction = raw.split(".", maxsplit=1)
            if language == "vi":
                spoken = f"{num2words(int(whole), lang='vi')} phẩy " + " ".join(num2words(int(d), lang="vi") for d in fraction)
            else:
                spoken = f"{num2words(int(whole), lang='en')} point " + " ".join(num2words(int(d), lang="en") for d in fraction)
        unit_words = UNIT_WORDS[language].get(unit, "")
        return f"{spoken} {unit_words}".strip()
