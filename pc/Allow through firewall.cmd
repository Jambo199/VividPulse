@echo off
rem Only needed if your phone can't find the PC: adds Windows Firewall rules for home networks.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0VividPulsePC.ps1" -AllowFirewall
