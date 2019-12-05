# ORMToBraille
Scannea una partitura y la genera un archivo en braille

## Dependencias

[Java Development Kit (JDK)][java_dev]: version 7 or 8 (preferred), but not 9 or 10 yet

[Git][git]: Controlador de versiones

[Tesseract][tesseract]: Audiveris delega todo trabajo de reconocimiento de texto y caracteres en Tesseract

## Compilación

Para compilar el programa se usa git

- git clone https://github.com/javvMonroy/ORMToBraille.git
- cd ORMToBraille
- En windows: gradlew.bat build
- Mac  y Linux: ./gradlew build

[java_dev]: https://www.oracle.com/technetwork/java/javase/downloads/index.html
[git]: https://git-scm.com
[tesseract]: https://github.com/tesseract-ocr/tesseract