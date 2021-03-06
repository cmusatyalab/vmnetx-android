static const guint16 keymap_android2xtkbd[] = {
  [0x3] = 0x12d,       /* 3 (AKEYCODE_HOME) => 301 via 204 (KEY_DASHBOARD) */
  [0x4] = 0x16a,       /* 4 (AKEYCODE_BACK) => 362 via 158 (KEY_BACK) */
  [0x5] = 0x63,        /* 5 (AKEYCODE_CALL) => 99 via 169 (KEY_PHONE) */
  [0x7] = 0xb,         /* 7 (AKEYCODE_0) => 11 via 11 (KEY_0) */
  [0x8] = 0x2,         /* 8 (AKEYCODE_1) => 2 via 2 (KEY_1) */
  [0x9] = 0x3,         /* 9 (AKEYCODE_2) => 3 via 3 (KEY_2) */
  [0xa] = 0x4,         /* 10 (AKEYCODE_3) => 4 via 4 (KEY_3) */
  [0xb] = 0x5,         /* 11 (AKEYCODE_4) => 5 via 5 (KEY_4) */
  [0xc] = 0x6,         /* 12 (AKEYCODE_5) => 6 via 6 (KEY_5) */
  [0xd] = 0x7,         /* 13 (AKEYCODE_6) => 7 via 7 (KEY_6) */
  [0xe] = 0x8,         /* 14 (AKEYCODE_7) => 8 via 8 (KEY_7) */
  [0xf] = 0x9,         /* 15 (AKEYCODE_8) => 9 via 9 (KEY_8) */
  [0x10] = 0xa,        /* 16 (AKEYCODE_9) => 10 via 10 (KEY_9) */
  [0x13] = 0x148,      /* 19 (AKEYCODE_DPAD_UP) => 328 via 103 (KEY_UP) */
  [0x14] = 0x150,      /* 20 (AKEYCODE_DPAD_DOWN) => 336 via 108 (KEY_DOWN) */
  [0x15] = 0x14b,      /* 21 (AKEYCODE_DPAD_LEFT) => 331 via 105 (KEY_LEFT) */
  [0x16] = 0x14d,      /* 22 (AKEYCODE_DPAD_RIGHT) => 333 via 106 (KEY_RIGHT) */
  [0x18] = 0x130,      /* 24 (AKEYCODE_VOLUME_UP) => 304 via 115 (KEY_VOLUMEUP) */
  [0x19] = 0x12e,      /* 25 (AKEYCODE_VOLUME_DOWN) => 302 via 114 (KEY_VOLUMEDOWN) */
  [0x1a] = 0x15e,      /* 26 (AKEYCODE_POWER) => 350 via 116 (KEY_POWER) */
  [0x1b] = 0x13b,      /* 27 (AKEYCODE_CAMERA) => 315 via 212 (KEY_CAMERA) */
  [0x1d] = 0x1e,       /* 29 (AKEYCODE_A) => 30 via 30 (KEY_A) */
  [0x1e] = 0x30,       /* 30 (AKEYCODE_B) => 48 via 48 (KEY_B) */
  [0x1f] = 0x2e,       /* 31 (AKEYCODE_C) => 46 via 46 (KEY_C) */
  [0x20] = 0x20,       /* 32 (AKEYCODE_D) => 32 via 32 (KEY_D) */
  [0x21] = 0x12,       /* 33 (AKEYCODE_E) => 18 via 18 (KEY_E) */
  [0x22] = 0x21,       /* 34 (AKEYCODE_F) => 33 via 33 (KEY_F) */
  [0x23] = 0x22,       /* 35 (AKEYCODE_G) => 34 via 34 (KEY_G) */
  [0x24] = 0x23,       /* 36 (AKEYCODE_H) => 35 via 35 (KEY_H) */
  [0x25] = 0x17,       /* 37 (AKEYCODE_I) => 23 via 23 (KEY_I) */
  [0x26] = 0x24,       /* 38 (AKEYCODE_J) => 36 via 36 (KEY_J) */
  [0x27] = 0x25,       /* 39 (AKEYCODE_K) => 37 via 37 (KEY_K) */
  [0x28] = 0x26,       /* 40 (AKEYCODE_L) => 38 via 38 (KEY_L) */
  [0x29] = 0x32,       /* 41 (AKEYCODE_M) => 50 via 50 (KEY_M) */
  [0x2a] = 0x31,       /* 42 (AKEYCODE_N) => 49 via 49 (KEY_N) */
  [0x2b] = 0x18,       /* 43 (AKEYCODE_O) => 24 via 24 (KEY_O) */
  [0x2c] = 0x19,       /* 44 (AKEYCODE_P) => 25 via 25 (KEY_P) */
  [0x2d] = 0x10,       /* 45 (AKEYCODE_Q) => 16 via 16 (KEY_Q) */
  [0x2e] = 0x13,       /* 46 (AKEYCODE_R) => 19 via 19 (KEY_R) */
  [0x2f] = 0x1f,       /* 47 (AKEYCODE_S) => 31 via 31 (KEY_S) */
  [0x30] = 0x14,       /* 48 (AKEYCODE_T) => 20 via 20 (KEY_T) */
  [0x31] = 0x16,       /* 49 (AKEYCODE_U) => 22 via 22 (KEY_U) */
  [0x32] = 0x2f,       /* 50 (AKEYCODE_V) => 47 via 47 (KEY_V) */
  [0x33] = 0x11,       /* 51 (AKEYCODE_W) => 17 via 17 (KEY_W) */
  [0x34] = 0x2d,       /* 52 (AKEYCODE_X) => 45 via 45 (KEY_X) */
  [0x35] = 0x15,       /* 53 (AKEYCODE_Y) => 21 via 21 (KEY_Y) */
  [0x36] = 0x2c,       /* 54 (AKEYCODE_Z) => 44 via 44 (KEY_Z) */
  [0x37] = 0x33,       /* 55 (AKEYCODE_COMMA) => 51 via 51 (KEY_COMMA) */
  [0x38] = 0x34,       /* 56 (AKEYCODE_PERIOD) => 52 via 52 (KEY_DOT) */
  [0x39] = 0x38,       /* 57 (AKEYCODE_ALT_LEFT) => 56 via 56 (KEY_LEFTALT) */
  [0x3a] = 0x138,      /* 58 (AKEYCODE_ALT_RIGHT) => 312 via 100 (KEY_RIGHTALT) */
  [0x3b] = 0x2a,       /* 59 (AKEYCODE_SHIFT_LEFT) => 42 via 42 (KEY_LEFTSHIFT) */
  [0x3c] = 0x36,       /* 60 (AKEYCODE_SHIFT_RIGHT) => 54 via 54 (KEY_RIGHTSHIFT) */
  [0x3d] = 0xf,        /* 61 (AKEYCODE_TAB) => 15 via 15 (KEY_TAB) */
  [0x3e] = 0x39,       /* 62 (AKEYCODE_SPACE) => 57 via 57 (KEY_SPACE) */
  [0x40] = 0x102,      /* 64 (AKEYCODE_EXPLORER) => 258 via 150 (KEY_WWW) */
  [0x41] = 0x16c,      /* 65 (AKEYCODE_ENVELOPE) => 364 via 155 (KEY_MAIL) */
  [0x42] = 0x1c,       /* 66 (AKEYCODE_ENTER) => 28 via 28 (KEY_ENTER) */
  [0x43] = 0xe,        /* 67 (AKEYCODE_DEL) => 14 via 14 (KEY_BACKSPACE) */
  [0x44] = 0x29,       /* 68 (AKEYCODE_GRAVE) => 41 via 41 (KEY_GRAVE) */
  [0x45] = 0xc,        /* 69 (AKEYCODE_MINUS) => 12 via 12 (KEY_MINUS) */
  [0x46] = 0xd,        /* 70 (AKEYCODE_EQUALS) => 13 via 13 (KEY_EQUAL) */
  [0x47] = 0x1a,       /* 71 (AKEYCODE_LEFT_BRACKET) => 26 via 26 (KEY_LEFTBRACE) */
  [0x48] = 0x1b,       /* 72 (AKEYCODE_RIGHT_BRACKET) => 27 via 27 (KEY_RIGHTBRACE) */
  [0x49] = 0x2b,       /* 73 (AKEYCODE_BACKSLASH) => 43 via 43 (KEY_BACKSLASH) */
  [0x4a] = 0x27,       /* 74 (AKEYCODE_SEMICOLON) => 39 via 39 (KEY_SEMICOLON) */
  [0x4b] = 0x28,       /* 75 (AKEYCODE_APOSTROPHE) => 40 via 40 (KEY_APOSTROPHE) */
  [0x4c] = 0x35,       /* 76 (AKEYCODE_SLASH) => 53 via 53 (KEY_SLASH) */
  [0x4f] = 0x16d,      /* 79 (AKEYCODE_HEADSETHOOK) => 365 via 226 (KEY_MEDIA) */
  [0x52] = 0x15d,      /* 82 (AKEYCODE_MENU) => 349 via 127 (KEY_COMPOSE) */
  [0x54] = 0x165,      /* 84 (AKEYCODE_SEARCH) => 357 via 217 (KEY_SEARCH) */
  [0x55] = 0x122,      /* 85 (AKEYCODE_MEDIA_PLAY_PAUSE) => 290 via 164 (KEY_PLAYPAUSE) */
  [0x56] = 0x124,      /* 86 (AKEYCODE_MEDIA_STOP) => 292 via 166 (KEY_STOPCD) */
  [0x57] = 0x119,      /* 87 (AKEYCODE_MEDIA_NEXT) => 281 via 163 (KEY_NEXTSONG) */
  [0x58] = 0x110,      /* 88 (AKEYCODE_MEDIA_PREVIOUS) => 272 via 165 (KEY_PREVIOUSSONG) */
  [0x59] = 0x118,      /* 89 (AKEYCODE_MEDIA_REWIND) => 280 via 168 (KEY_REWIND) */
  [0x5a] = 0x134,      /* 90 (AKEYCODE_MEDIA_FAST_FORWARD) => 308 via 208 (KEY_FASTFORWARD) */
  [0x5c] = 0x149,      /* 92 (AKEYCODE_PAGE_UP) => 329 via 104 (KEY_PAGEUP) */
  [0x5d] = 0x151,      /* 93 (AKEYCODE_PAGE_DOWN) => 337 via 109 (KEY_PAGEDOWN) */
  [0x6f] = 0x1,        /* 111 (AKEYCODE_ESCAPE) => 1 via 1 (KEY_ESC) */
  [0x70] = 0x153,      /* 112 (AKEYCODE_FORWARD_DEL) => 339 via 111 (KEY_DELETE) */
  [0x71] = 0x1d,       /* 113 (AKEYCODE_CTRL_LEFT) => 29 via 29 (KEY_LEFTCTRL) */
  [0x72] = 0x11d,      /* 114 (AKEYCODE_CTRL_RIGHT) => 285 via 97 (KEY_RIGHTCTRL) */
  [0x73] = 0x3a,       /* 115 (AKEYCODE_CAPS_LOCK) => 58 via 58 (KEY_CAPSLOCK) */
  [0x74] = 0x46,       /* 116 (AKEYCODE_SCROLL_LOCK) => 70 via 70 (KEY_SCROLLLOCK) */
  [0x75] = 0x15b,      /* 117 (AKEYCODE_META_LEFT) => 347 via 125 (KEY_LEFTMETA) */
  [0x76] = 0x15c,      /* 118 (AKEYCODE_META_RIGHT) => 348 via 126 (KEY_RIGHTMETA) */
  [0x78] = 0x54,       /* 120 (AKEYCODE_SYSRQ) => 84 via 99 (KEY_SYSRQ) */
  [0x79] = 0x146,      /* 121 (AKEYCODE_BREAK) => 326 via 119 (KEY_PAUSE) */
  [0x7a] = 0x147,      /* 122 (AKEYCODE_MOVE_HOME) => 327 via 102 (KEY_HOME) */
  [0x7b] = 0x14f,      /* 123 (AKEYCODE_MOVE_END) => 335 via 107 (KEY_END) */
  [0x7c] = 0x152,      /* 124 (AKEYCODE_INSERT) => 338 via 110 (KEY_INSERT) */
  [0x7d] = 0x169,      /* 125 (AKEYCODE_FORWARD) => 361 via 159 (KEY_FORWARD) */
  [0x7e] = 0x128,      /* 126 (AKEYCODE_MEDIA_PLAY) => 296 via 200 (KEY_PLAYCD) */
  [0x7f] = 0x129,      /* 127 (AKEYCODE_MEDIA_PAUSE) => 297 via 201 (KEY_PAUSECD) */
  [0x80] = 0x123,      /* 128 (AKEYCODE_MEDIA_CLOSE) => 291 via 160 (KEY_CLOSECD) */
  [0x81] = 0x6c,       /* 129 (AKEYCODE_MEDIA_EJECT) => 108 via 161 (KEY_EJECTCD) */
  [0x82] = 0x131,      /* 130 (AKEYCODE_MEDIA_RECORD) => 305 via 167 (KEY_RECORD) */
  [0x83] = 0x3b,       /* 131 (AKEYCODE_F1) => 59 via 59 (KEY_F1) */
  [0x84] = 0x3c,       /* 132 (AKEYCODE_F2) => 60 via 60 (KEY_F2) */
  [0x85] = 0x3d,       /* 133 (AKEYCODE_F3) => 61 via 61 (KEY_F3) */
  [0x86] = 0x3e,       /* 134 (AKEYCODE_F4) => 62 via 62 (KEY_F4) */
  [0x87] = 0x3f,       /* 135 (AKEYCODE_F5) => 63 via 63 (KEY_F5) */
  [0x88] = 0x40,       /* 136 (AKEYCODE_F6) => 64 via 64 (KEY_F6) */
  [0x89] = 0x41,       /* 137 (AKEYCODE_F7) => 65 via 65 (KEY_F7) */
  [0x8a] = 0x42,       /* 138 (AKEYCODE_F8) => 66 via 66 (KEY_F8) */
  [0x8b] = 0x43,       /* 139 (AKEYCODE_F9) => 67 via 67 (KEY_F9) */
  [0x8c] = 0x44,       /* 140 (AKEYCODE_F10) => 68 via 68 (KEY_F10) */
  [0x8d] = 0x57,       /* 141 (AKEYCODE_F11) => 87 via 87 (KEY_F11) */
  [0x8e] = 0x58,       /* 142 (AKEYCODE_F12) => 88 via 88 (KEY_F12) */
  [0x8f] = 0x45,       /* 143 (AKEYCODE_NUM_LOCK) => 69 via 69 (KEY_NUMLOCK) */
  [0x90] = 0x52,       /* 144 (AKEYCODE_NUMPAD_0) => 82 via 82 (KEY_KP0) */
  [0x91] = 0x4f,       /* 145 (AKEYCODE_NUMPAD_1) => 79 via 79 (KEY_KP1) */
  [0x92] = 0x50,       /* 146 (AKEYCODE_NUMPAD_2) => 80 via 80 (KEY_KP2) */
  [0x93] = 0x51,       /* 147 (AKEYCODE_NUMPAD_3) => 81 via 81 (KEY_KP3) */
  [0x94] = 0x4b,       /* 148 (AKEYCODE_NUMPAD_4) => 75 via 75 (KEY_KP4) */
  [0x95] = 0x4c,       /* 149 (AKEYCODE_NUMPAD_5) => 76 via 76 (KEY_KP5) */
  [0x96] = 0x4d,       /* 150 (AKEYCODE_NUMPAD_6) => 77 via 77 (KEY_KP6) */
  [0x97] = 0x47,       /* 151 (AKEYCODE_NUMPAD_7) => 71 via 71 (KEY_KP7) */
  [0x98] = 0x48,       /* 152 (AKEYCODE_NUMPAD_8) => 72 via 72 (KEY_KP8) */
  [0x99] = 0x49,       /* 153 (AKEYCODE_NUMPAD_9) => 73 via 73 (KEY_KP9) */
  [0x9a] = 0x135,      /* 154 (AKEYCODE_NUMPAD_DIVIDE) => 309 via 98 (KEY_KPSLASH) */
  [0x9b] = 0x37,       /* 155 (AKEYCODE_NUMPAD_MULTIPLY) => 55 via 55 (KEY_KPASTERISK) */
  [0x9c] = 0x4a,       /* 156 (AKEYCODE_NUMPAD_SUBTRACT) => 74 via 74 (KEY_KPMINUS) */
  [0x9d] = 0x4e,       /* 157 (AKEYCODE_NUMPAD_ADD) => 78 via 78 (KEY_KPPLUS) */
  [0x9e] = 0x53,       /* 158 (AKEYCODE_NUMPAD_DOT) => 83 via 83 (KEY_KPDOT) */
  [0x9f] = 0x7e,       /* 159 (AKEYCODE_NUMPAD_COMMA) => 126 via 121 (KEY_KPCOMMA) */
  [0xa0] = 0x11c,      /* 160 (AKEYCODE_NUMPAD_ENTER) => 284 via 96 (KEY_KPENTER) */
  [0xa1] = 0x59,       /* 161 (AKEYCODE_NUMPAD_EQUALS) => 89 via 117 (KEY_KPEQUAL) */
  [0xa2] = 0x176,      /* 162 (AKEYCODE_NUMPAD_LEFT_PAREN) => 374 via 179 (KEY_KPLEFTPAREN) */
  [0xa3] = 0x17b,      /* 163 (AKEYCODE_NUMPAD_RIGHT_PAREN) => 379 via 180 (KEY_KPRIGHTPAREN) */
  [0xa4] = 0x120,      /* 164 (AKEYCODE_VOLUME_MUTE) => 288 via 113 (KEY_MUTE) */
  [0xb0] = 0x66,       /* 176 (AKEYCODE_SETTINGS) => 102 via 141 (KEY_SETUP) */
  [0xd1] = 0x13d,      /* 209 (AKEYCODE_MUSIC) => 317 via 213 (KEY_SOUND) */
  [0xd2] = 0x121,      /* 210 (AKEYCODE_CALCULATOR) => 289 via 140 (KEY_CALC) */
  [0xd3] = 0x76,       /* 211 (AKEYCODE_ZENKAKU_HANKAKU) => 118 via 85 (KEY_ZENKAKUHANKAKU) */
  [0xd5] = 0x7b,       /* 213 (AKEYCODE_MUHENKAN) => 123 via 94 (KEY_MUHENKAN) */
  [0xd6] = 0x79,       /* 214 (AKEYCODE_HENKAN) => 121 via 92 (KEY_HENKAN) */
  [0xd7] = 0x70,       /* 215 (AKEYCODE_KATAKANA_HIRAGANA) => 112 via 93 (KEY_KATAKANAHIRAGANA) */
  [0xd8] = 0x7d,       /* 216 (AKEYCODE_YEN) => 125 via 124 (KEY_YEN) */
  [0xd9] = 0x73,       /* 217 (AKEYCODE_RO) => 115 via 89 (KEY_RO) */
  [0xdc] = 0x14c,      /* 220 (AKEYCODE_BRIGHTNESS_DOWN) => 332 via 224 (KEY_BRIGHTNESSDOWN) */
  [0xdd] = 0x154,      /* 221 (AKEYCODE_BRIGHTNESS_UP) => 340 via 225 (KEY_BRIGHTNESSUP) */
  [0xdf] = 0x15f,      /* 223 (AKEYCODE_SLEEP) => 351 via 142 (KEY_SLEEP) */
  [0xe0] = 0x163,      /* 224 (AKEYCODE_WAKEUP) => 355 via 143 (KEY_WAKEUP) */
  [0x103] = 0x175,     /* 259 (AKEYCODE_HELP) => 373 via 138 (KEY_HELP) */
};
