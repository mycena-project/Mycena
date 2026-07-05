// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project

import deCopy from "@data-handling-copy/de.json";
import enCopy from "@data-handling-copy/en.json";

const LOCALES = { de: deCopy, en: enCopy };

function resolveLocaleTag() {
  const lang = (navigator.language || "en").split("-")[0].toLowerCase();
  return LOCALES[lang] ? lang : "en";
}

/** @returns {typeof enCopy} */
export function loadDataHandlingCopy() {
  return LOCALES[resolveLocaleTag()] ?? enCopy;
}
