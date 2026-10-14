Vivid Pulse PC
==============

Lets Vivid Pulse on your phone see this PC: its specs, live CPU/GPU/RAM use, GPU temperature
(NVIDIA), what you're playing, your Steam library (Purchased and Installed) and Steam downloads,
and start a Steam game from your phone.

1. Unzip this folder somewhere you'll keep it, e.g. Documents\Vivid Pulse PC.
2. Open VividPulsePC. If Windows says it protected your PC, click "More info", then "Run anyway".
   If Windows Firewall asks, tick "Private networks" and click "Allow access".
3. Its window shows a 6-digit code. On your phone: Vivid Pulse > PC > Find my PC, pick this PC
   and type the code.

It lives in the icon by the clock (it may be under the ^ arrow). Click the icon for the code and
settings; right-click it to quit. Tick "Start with Windows" in its window to have it start (in
the background) whenever you sign in, so your phone can always reach it.

If the phone can't find the PC: both need the same Wi-Fi, and Windows should see your network as
Private (Settings > Network & internet > your connection > Network profile type). The window's
"Allow through firewall" button opens Windows Firewall's list of allowed apps, where you can tick
"Private" for VividPulsePC.

If your antivirus quarantines it (some flag new, unsigned programs by guesswork, e.g.
Malwarebytes "MachineLearning/Anomalous"), restore it and add the folder to its allow list.

It only answers on your local network, only to a phone with the code, and it only reads
information, apart from starting a Steam game when you ask from the phone. The code is kept in
%APPDATA%\VividPulsePC\config.json, with a short log next to it.

Moving from the first version (the black PowerShell window)? Close that window first. The new
one keeps the same code, so your phone stays paired.
