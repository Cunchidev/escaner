# Escáner

App Android para escanear documentos coa cámara, gardalos ordenados e sacalos en PDF ou JPG.
Sen publicidade, sen contas e sen subscricións. Interface en galego.

Naceu como substituto persoal dun escáner cheo de anuncios: fai unha cousa e faina sen estorbar.

## Que fai

- **Escanear** coa cámara: detección de bordes, recorte, filtros e varias páxinas seguidas.
- **Importar da galería** fotos xa feitas e tratalas como un escaneo.
- **Biblioteca** — «A túa mesa»: os documentos vense como follas de papel, en grella ou en lista, con carpetas de cores.
- **Buscar polo contido**: o texto de cada páxina recoñécese no propio móbil e pódese buscar por el, non só polo título.
- **Editar o documento**: engadir, reordenar e borrar páxinas, renomear e mover de carpeta.
- **Exportar**: compartir como PDF ou como JPG, ou gardar o PDF onde queiras.
- **Copiar o texto** recoñecido dun documento.
- **Aparencia**: seis cores de acento, vista en follas ou lista e tres maneiras de ordenar. Tema claro e escuro.

## Privacidade

- Os documentos gárdanse no almacenamento privado da app. Non hai servidor nin sincronización.
- A app non pide permiso de cámara: a cámara ábrea o escáner de Google Play Services.
- O recoñecemento de texto vai dentro da app e funciona sen conexión.
- O código da app non fai ningunha conexión de rede. As librarías de ML Kit, de Google, engaden o permiso de
  Internet para a súa propia telemetría de uso (que función se chamou, canto tardou, modelo de móbil); segundo
  a documentación de Google, as imaxes e o texto non saen do dispositivo.

## Requisitos

- Android 8.0 (API 26) ou superior.
- Móbil ARM de 64 bits.
- Google Play Services actualizado (é quen pon o escáner).

## Compilar

Fai falta o Android SDK e JDK 17. Crea un `local.properties` coa ruta do SDK (`sdk.dir=...`) ou abre o proxecto
con Android Studio, que o crea só.

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew assembleRelease
```

O APK queda en `app/build/outputs/apk/release/app-release.apk` (uns 15 MB). Vai optimizado con R8 e asinado coa
clave de debug da túa máquina, que abonda para instalalo a man; para publicalo habería que poñerlle unha clave propia.

## Como está feita

| | |
|---|---|
| Linguaxe e interface | Kotlin, Jetpack Compose, Material 3 |
| Escáner | ML Kit Document Scanner |
| Recoñecemento de texto | ML Kit Text Recognition (modelo latino embebido) |
| Datos | Room, con busca de texto completo (FTS4) |
| Inxección de dependencias | Hilt |

Algúns detalles:

- **O PDF non se garda**: xérase a partir dos JPG cada vez que se exporta, así que cambiar as páxinas nunca deixa
  nada desfasado.
- **Escritor de PDF propio** (`JpegPdfWriter`): mete cada JPG tal cal dentro do PDF, sen recomprimir. Os ficheiros
  saen pequenos e non se perde calidade. O PDF é de imaxe, sen capa de texto.
- **A orde das páxinas** é unha lista de nomes de ficheiro; reordenar non move nin renomea nada no disco.

```
app/src/main/java/com/cunchidev/escaner/
  data/   base de datos, ficheiros das páxinas, recoñecemento de texto e PDF
  ui/     tema, biblioteca e visor de documentos
```

## Estado

Proxecto persoal, feito para un uso concreto e probado nun só móbil. Úsao e adáptao como queiras, pero non
esperes soporte nin que cubra todos os casos.
