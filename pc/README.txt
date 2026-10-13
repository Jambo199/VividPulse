Vivid Pulse PC
==============

Lets Vivid Pulse on your phone see this PC: its specs, live CPU/GPU/RAM use, GPU temperature
(NVIDIA), what you're playing, and your Steam library (installed games, sizes, hours played), and
start a Steam game from your phone.

1. Unzip this folder somewhere you'll keep it (e.g. Documents\Vivid Pulse PC).
2. Double-click "Start Vivid Pulse PC.cmd". If Windows says it protected your PC, click
   "More info" then "Run anyway". If Windows Firewall asks, allow it on private networks.
3. On your phone: Vivid Pulse > PC > Find my PC, pick this PC and type the code the window shows.

Keep the window open while you want the phone connected (minimise it).

"Start with Windows.cmd" makes it start (minimised) whenever you sign in.
"Allow through firewall.cmd" adds the firewall rules if the phone can't find the PC.

It only answers on your home network, only to a phone with the code, and it only reads
information, apart from starting a Steam game when you ask from the phone. The code is kept in
%APPDATA%\VividPulsePC\config.json; delete that file to get a new code.
