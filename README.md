<p align="center">
  <img src="assets/logo.png" width="128" height="128" alt="Luminary IDE Logo" />
</p>

<h1 align="center">Luminary IDE</h1>

<p align="center">
  <strong>A modern, lightweight desktop text and code editor developed using Java.</strong><br>
  Built with pure Java Swing and AWT — zero third-party dependencies, fast startup, and 12 built-in themes.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk" alt="Java 17+" />
  <img src="https://img.shields.io/badge/UI-Swing%20%2F%20AWT-orange" alt="Swing / AWT" />
  <img src="https://img.shields.io/badge/Dependencies-0%20(Pure%20JDK)-brightgreen" alt="Dependencies" />
  <img src="https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey" alt="Cross Platform" />
  <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT" />
</p>

---

## 📖 Overview

**Luminary IDE** fills the gap between basic text editors like Notepad (which lack syntax colors and crash protection) and heavyweight IDEs like IntelliJ or VS Code (which have slow startup times and high memory overhead for quick file edits).

Developed as a single, self-contained Java source file, Luminary IDE delivers a full-featured desktop editing experience with rich theme switching, syntax highlighting, regex find-and-replace, inline image support, and auto-save crash recovery — using nothing outside the standard Java Development Kit (JDK).

---

## 📸 Screenshots

| Main Editor (Dark Theme) | Welcome Screen |
| :---: | :---: |
| ![Editor Screen](assets/screenshots/editor.png) | ![Welcome Screen](assets/screenshots/welcome.png) |

| Secure Sign-In Dialog |
| :---: |
| ![Login Screen](assets/screenshots/login.png) |

---

## ✨ Features

- 🎨 **12 Built-In Themes**: Light, Dark, Ocean, Sunset, Forest, Dracula, Nord, Monokai, Cyberpunk, Lavender, Solarized Light, and High Contrast.
- 🌈 **Syntax Highlighting & Rainbow Brackets**: Per-language color highlighting for Java, Python, JavaScript, TypeScript, C/C++, and JSON. Bracket colors dynamically reflect nesting depth.
- 🔢 **Line Numbers & Current Line Highlight**: Custom line gutter with current line indicator and active line highlight in the editor.
- ⚡ **Smart Editing Tools**:
  - Auto-indent on Enter
  - Auto-close pairs for parentheses, brackets, braces, and quotes
  - Duplicate Line (`Ctrl+D`)
  - Move Line Up / Down (`Alt+Up` / `Alt+Down`)
  - Toggle Line Comments (`Ctrl+/`) with language-aware comment markers (`//` or `#`)
  - Go to Line (`Ctrl+G`)
- 🔍 **Find & Replace**: Case-sensitive search, regular expression support, live match counter (`1 of 3 matches`), previous/next navigation, and Replace All.
- 🖼️ **Inline Images & Image Viewer**: Paste images from clipboard or drag-and-drop into text; view and inspect images with smooth zoom, 100%, and fit controls.
- 🛡️ **Crash Recovery & Auto-Draft**: Unsaved changes auto-saved to an emergency draft every 20 seconds. On unexpected shutdown, a restore prompt recovers unsaved work.
- 🔐 **Secure Local Authentication**: User registration and login using PBKDF2 with HMAC-SHA256 (120,000 rounds) and random 16-byte salt, accompanied by password validation rules and a Guest Mode.
- 🔊 **Sound Manager**: Synthesized audio feedback for startup, key clicks, saves, theme changes, and errors (fully customizable volume and mute toggles).

---

## ⌨️ Keyboard Shortcuts

| Shortcut | Action |
| --- | --- |
| `Ctrl + N` | New File |
| `Ctrl + Shift + N` | New Window |
| `Ctrl + O` | Open File |
| `Ctrl + S` | Save File |
| `Ctrl + Shift + S` | Save As |
| `Ctrl + Shift + I` | Insert Image |
| `Ctrl + Z` | Undo |
| `Ctrl + Y` / `Ctrl + Shift + Z` | Redo |
| `Ctrl + X` / `Ctrl + C` / `Ctrl + V` | Cut / Copy / Paste |
| `Ctrl + A` | Select All |
| `Ctrl + D` | Duplicate Line |
| `Alt + Up` / `Alt + Down` | Move Line Up / Down |
| `Ctrl + /` | Toggle Line Comment |
| `Ctrl + G` | Go to Line |
| `Ctrl + F` | Find |
| `Ctrl + H` | Replace |
| `Ctrl + +` / `Ctrl + -` | Zoom In / Zoom Out |
| `Ctrl + 0` | Reset Zoom (14 pt) |
| `F5` | Insert Current Date & Time |
| `Esc` | Close Find & Replace Bar |

---

## 🚀 Getting Started

### Prerequisites

- **Java JDK 17** or higher installed (Tested on OpenJDK 17, 21, and 25).
- Ensure `java` and `javac` are available in your system `PATH`.

### Compile and Run

Clone the repository:
```bash
git clone https://github.com/HariAcharyaKhatri/LuminaryIDE.git
cd LuminaryIDE
```

Compile the source code:
```bash
javac -d out src/LuminaryIDE.java
```

Run the application:
```bash
java -cp out LuminaryIDE
```

Open a specific file directly:
```bash
java -cp out LuminaryIDE Demo.java
```

### Running with IntelliJ IDEA, Eclipse, or NetBeans

1. Open the project folder in your IDE.
2. Ensure the project SDK is set to **Java 17+**.
3. Locate `src/LuminaryIDE.java` and click **Run**.

---

## 📁 Project Structure

```
Luminary IDE/
├── assets/
│   ├── logo.svg              # Vector logo
│   ├── logo.png              # High-resolution (512x512) app logo
│   ├── icon-*.png            # Multi-resolution icons (16px to 256px)
│   └── screenshots/          # Application screenshots
│       ├── editor.png
│       ├── login.png
│       └── welcome.png
├── src/
│   ├── LuminaryIDE.java      # Complete application source code
│   └── assets/               # Bundled runtime image assets
├── .gitignore                # Git ignore configuration
├── Luminary IDE.iml          # IntelliJ project module
└── README.md                 # Project documentation
```

---

## 🎓 Academic Details

- **Project Title**: Luminary IDE — A Modern Desktop Text and Code Editor Developed Using Java
- **Student**: Hari Khatri (Roll No: BCA307)
- **Course**: Bachelor of Computer Applications (BCA) — 3rd Semester
- **Institution**: Adarsh College of Professional Studies, Department of Computer Science, Aburoad, Rajasthan
- **Project Guide**: Ms. Deepika Kumari Ma'am, Assistant Professor

---

## 👤 Author

- **Hari Khatri**
- **GitHub**: [@HariAcharyaKhatri](https://github.com/HariAcharyaKhatri)
- **Email**: [hariacharyakhatri@gmail.com](mailto:hariacharyakhatri@gmail.com)

---

## 📄 License

This project is open-source and available under the [MIT License](LICENSE).
