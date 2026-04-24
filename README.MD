# Observer

**Observer** is an open-source Android application designed to serve as a digital "panic button" for high-stakes documentation. It enables users to stream live video and audio directly to a private, specialized server via the RTMP protocol. 

The primary mission of Observer is to safeguard evidence in real-time. By offloading data to a remote server as it is recorded, the application ensures that critical footage is preserved even if the recording device is seized, damaged, or destroyed during an incident (e.g., during encounters with law enforcement or while witnessing a crime).

---

## Key Features

* **Real-time RTMP Streaming:** Direct transmission of video/audio streams to your self-hosted or preferred RTMP server.
* **Evidence Protection:** Data is stored remotely instantly, making physical device destruction an ineffective means of suppressing evidence.
* **Minimalist Interface:** Designed for quick activation in high-stress situations.
* **Modern Android Integration:** Built for the latest Android standards while maintaining broad compatibility.

## Technical Specifications

| Feature | Requirement |
| :--- | :--- |
| **Target SDK** | 36 |
| **Minimum SDK** | 24 (Android 7.0+) |
| **Protocol** | RTMP (Real-Time Messaging Protocol) |
| **Development** | Built with assistance from Claude AI |
| **License** | GNU GPL-3.0-ONLY |

---

## How It Works

1. **Configuration:** The user inputs their specific RTMP server URL and stream key into the app settings.
2. **Activation:** Upon launching the app or hitting the record button, the stream begins immediately.
3. **Transmission:** Audio and video data bypass local storage bottlenecks and are sent over the network.
4. **Redundancy:** Even if the phone is turned off or broken mid-stream, the server retains everything received up to the last second of connection.

## Installation & Setup

1. **Prerequisites:** You will need a server configured to ingest RTMP streams (such as Nginx with the RTMP module, SRS, or MistServer).
2. **Building from Source:**
   * Clone the repository.
   * Open the project in Android Studio.
   * Ensure you have the Android SDK 36 platform installed.
   * Build the APK and install it on your device.

## Contributing

As a FOSS project, contributions are welcome. Whether it is improving the UI for better accessibility, optimizing the streaming buffer, or enhancing security protocols, feel free to open a pull request or report issues.

## Disclaimer

*This application is provided as-is. Users should ensure that their use of recording and streaming software complies with local laws regarding privacy and the recording of public or private individuals.*