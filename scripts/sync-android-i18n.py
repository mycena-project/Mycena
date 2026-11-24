#!/usr/bin/env python3
"""Merge Android strings.xml locales with base keys; fill missing via locale overlays."""
from __future__ import annotations

import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OVERLAY_DIR = ROOT / "scripts" / "overlays"
RES = ROOT / "android" / "app" / "src" / "main" / "res"
BASE = RES / "values" / "strings.xml"
LOCALES = {
    "de": RES / "values-de" / "strings.xml",
    "ar": RES / "values-ar" / "strings.xml",
    "ru": RES / "values-ru" / "strings.xml",
    "fa": RES / "values-fa" / "strings.xml",
    "zh": RES / "values-zh-rCN" / "strings.xml",
}

# Inline overlays for locales without a JSON file; merged with scripts/overlays/{loc}.json.
OVERLAYS: dict[str, dict[str, str]] = {
    "de": {},
    "ar": {
        "chat_pending_requests": "طلبات جهات الاتصال",
        "chat_contacts": "جهات الاتصال",
        "chat_accept_contact": "قبول",
        "chat_reject_contact": "رفض",
        "chat_no_conversations": "لا محادثات بعد.\nامسح رمز QR للاتصال.",
        "chat_online": "متصل",
        "chat_offline": "غير متصل",
        "peers_nearby_title": "أجهزة قريبة",
        "peers_nearby_empty": "لا أجهزة أخرى على الشبكة بعد.",
        "peers_relay_online": "Relay: %1$d أقران",
        "peers_relay_offline": "Relay: غير متصل",
        "peers_scan_qr_prompt": "مسح QR للنظير",
        "peers_my_qr_cd": "رمز QR للدعوة",
        "peers_add_bootstrap": "إضافة",
        "peers_connect_via_relay": "عبر Relay",
        "peers_connect_relay_hint": "معرّف النظير → relay",
        "peers_connected_count": "%1$d متصل",
        "error_invalid_qr": "تنسيق QR غير صالح",
        "settings_rendezvous_title": "اكتشاف عبر Relay",
        "settings_rendezvous_sub": "العثور على مستخدمي Relay والظهور لهم (اختياري)",
        "discovery_channel_name": "عقد قريبة",
        "discovery_notification_title": "عقدة Mycena %1$s",
        "discovery_notification_body": "اضغط للاتصال (%1$s…)",
        "discovery_very_close": "قريب جداً",
        "discovery_nearby": "قريب",
        "discovery_in_range": "في النطاق",
        "language_system": "لغة النظام",
    },
    "ru": {
        "chat_pending_requests": "Запросы контактов",
        "chat_contacts": "Контакты",
        "chat_accept_contact": "Принять",
        "chat_reject_contact": "Отклонить",
        "chat_online": "в сети",
        "chat_offline": "не в сети",
        "peers_nearby_title": "Устройства рядом",
        "peers_nearby_empty": "Других устройств в сети пока нет.",
        "peers_relay_online": "Relay: %1$d узлов",
        "peers_relay_offline": "Relay: недоступен",
        "peers_scan_qr_prompt": "Сканировать QR узла",
        "peers_my_qr_cd": "QR приглашения",
        "peers_add_bootstrap": "Добавить",
        "peers_connect_via_relay": "Через relay",
        "peers_connect_relay_hint": "Peer ID → relay",
        "peers_connected_count": "Подключено: %1$d",
        "error_invalid_qr": "Неверный формат QR",
        "settings_rendezvous_title": "Обнаружение через relay",
        "settings_rendezvous_sub": "Искать других на relay и быть видимым (opt-in)",
        "discovery_channel_name": "Узлы рядом",
        "discovery_notification_title": "Узел Mycena %1$s",
        "discovery_notification_body": "Нажмите для подключения (%1$s…)",
        "discovery_very_close": "очень близко",
        "discovery_nearby": "рядом",
        "discovery_in_range": "в зоне",
        "language_system": "Язык системы",
    },
    "fa": {
        "chat_pending_requests": "درخواست‌های مخاطب",
        "chat_contacts": "مخاطبین",
        "chat_accept_contact": "پذیرفتن",
        "chat_reject_contact": "رد",
        "chat_online": "آنلاین",
        "chat_offline": "آفلاین",
        "peers_nearby_title": "دستگاه‌های نزدیک",
        "peers_nearby_empty": "هنوز دستگاه دیگری در شبکه دیده نمی‌شود.",
        "peers_relay_online": "Relay: %1$d همتا",
        "peers_relay_offline": "Relay: آفلاین",
        "peers_scan_qr_prompt": "اسکن QR همتا",
        "peers_my_qr_cd": "QR دعوت",
        "peers_add_bootstrap": "افزودن",
        "peers_connect_via_relay": "از طریق relay",
        "peers_connect_relay_hint": "شناسه همتا → relay",
        "peers_connected_count": "%1$d متصل",
        "error_invalid_qr": "قالب QR نامعتبر",
        "settings_rendezvous_title": "کشف از طریق relay",
        "settings_rendezvous_sub": "یافتن دیگران روی relay و دیده شدن (اختیاری)",
        "discovery_channel_name": "گره‌های نزدیک",
        "discovery_notification_title": "گره Mycena %1$s",
        "discovery_notification_body": "برای اتصال لمس کنید (%1$s…)",
        "discovery_very_close": "خیلی نزدیک",
        "discovery_nearby": "نزدیک",
        "discovery_in_range": "در برد",
        "language_system": "زبان سیستم",
    },
    "zh": {
        "chat_pending_requests": "联系人请求",
        "chat_contacts": "联系人",
        "chat_accept_contact": "接受",
        "chat_reject_contact": "拒绝",
        "chat_online": "在线",
        "chat_offline": "离线",
        "peers_nearby_title": "附近设备",
        "peers_nearby_empty": "网络上暂无其他设备。",
        "peers_relay_online": "中继：%1$d 个节点",
        "peers_relay_offline": "中继：离线",
        "peers_scan_qr_prompt": "扫描节点二维码",
        "peers_my_qr_cd": "邀请二维码",
        "peers_add_bootstrap": "添加",
        "peers_connect_via_relay": "经中继连接",
        "peers_connect_relay_hint": "节点 ID → 中继",
        "peers_connected_count": "已连接 %1$d 个",
        "error_invalid_qr": "二维码格式无效",
        "settings_rendezvous_title": "中继发现",
        "settings_rendezvous_sub": "发现其他中继用户并可见（可选）",
        "discovery_channel_name": "附近节点",
        "discovery_notification_title": "Mycena 节点 %1$s",
        "discovery_notification_body": "点击连接（%1$s…）",
        "discovery_very_close": "非常近",
        "discovery_nearby": "附近",
        "discovery_in_range": "范围内",
        "language_system": "系统语言",
    },
}


def load_overlay(loc: str) -> dict[str, str]:
    merged: dict[str, str] = dict(OVERLAYS.get(loc, {}))
    path = OVERLAY_DIR / f"{loc}.json"
    if path.exists():
        merged.update(json.loads(path.read_text(encoding="utf-8")))
    return merged


# Keys that should stay identical to English (brand, protocol tokens, examples).
KEEP_ENGLISH: frozenset[str] = frozenset(
    {
        "app_name",
        "settings_scope_key_placeholder",
        "bulletin_scope_example",
        "wallet_cold_wallet_hint",
    }
)


def parse_strings(path: Path) -> dict[str, str]:
    tree = ET.parse(path)
    out: dict[str, str] = {}
    for el in tree.getroot():
        if el.tag == "string" and el.attrib.get("name"):
            out[el.attrib["name"]] = el.text or ""
    return out


def escape_xml(text: str) -> str:
    return (
        text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", "\\'")
        .replace('"', '\\"')
    )


def write_strings(path: Path, strings: dict[str, str]) -> None:
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for name in sorted(strings.keys()):
        val = strings[name]
        # Preserve Android format placeholders; escape XML entities except apostrophe in attributes
        val = val.replace("&", "&amp;").replace("<", "&lt;")
        lines.append(f'    <string name="{name}">{val}</string>')
    lines.append("</resources>")
    lines.append("")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    base = parse_strings(BASE)
    # Add new base keys for Phase C if missing from parse (they should be in values/strings.xml)
    new_base = {
        "peers_nearby_title": "Nearby devices",
        "peers_nearby_empty": "No other devices visible on the network yet.",
        "peers_relay_online": "Relay: %1$d peers",
        "peers_relay_offline": "Relay: offline",
        "peers_scan_qr_prompt": "Scan peer QR",
        "peers_my_qr_cd": "My invite QR code",
        "peers_add_bootstrap": "Add",
        "peers_connect_via_relay": "Via relay",
        "peers_connect_relay_hint": "Peer ID → relay",
        "peers_connected_count": "%1$d connected",
        "error_invalid_qr": "Invalid QR format",
        "settings_rendezvous_title": "Relay discovery",
        "settings_rendezvous_sub": "Find other relay users and be discoverable (opt-in)",
        "chat_peer_subtitle_online": "%1$s · online",
        "chat_peer_subtitle_offline": "%1$s",
    }
    base.update(new_base)
    # Update base file too
    write_strings(BASE, base)

    try:
        from deep_translator import GoogleTranslator
    except ImportError:
        GoogleTranslator = None  # type: ignore

    for loc, path in LOCALES.items():
        existing = parse_strings(path) if path.exists() else {}
        merged = dict(existing)
        overlay = load_overlay(loc)
        missing = [k for k in base if k not in merged]
        stale = [
            k
            for k in base
            if k in merged
            and merged[k] == base[k]
            and k not in KEEP_ENGLISH
            and k not in overlay
        ]
        to_translate = sorted(set(missing) | set(stale))
        translated = 0
        if GoogleTranslator and to_translate:
            lang = {"de": "de", "ar": "ar", "ru": "ru", "fa": "fa", "zh": "zh-CN"}[loc]
            tr = GoogleTranslator(source="en", target=lang)
            for key in to_translate:
                if key in overlay:
                    merged[key] = overlay[key]
                    continue
                try:
                    merged[key] = tr.translate(base[key])
                    translated += 1
                except Exception:
                    merged[key] = base[key]
        else:
            for key in base:
                if key not in merged:
                    merged[key] = overlay.get(key, base[key])
        for key, val in overlay.items():
            merged[key] = val
        write_strings(path, merged)
        print(
            f"{loc}: {len(merged)} keys "
            f"({len(missing)} missing, {len(stale)} stale-en, {translated} translated)"
        )


if __name__ == "__main__":
    main()
