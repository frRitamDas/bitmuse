; Pexpo Music Windows 1.1 installer
; Publisher: Ritam Das
; Requires Inno Setup 6+

#define MyAppName "Pexpo Music"
#define MyAppVersion "1.1"
#define MyAppPublisher "Ritam Das"
#define MyAppURL "https://github.com/frRitamDas/bitmuse"
#define MyAppExeName "Pexpo.exe"

[Setup]
AppId={{D5F6A9A2-4E1D-4D1B-9C4B-PEXPO-WIN11}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\Pexpo Music
DefaultGroupName=Pexpo Music
OutputBaseFilename=Pexpo-1.1-Setup
OutputDir=..\dist
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
UninstallDisplayName=Pexpo Music
Uninstallable=yes

[Files]
Source: "..\build\Pexpo.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\Pexpo Music"; Filename: "{app}\Pexpo.exe"
Name: "{autodesktop}\Pexpo Music"; Filename: "{app}\Pexpo.exe"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"

[Run]
Filename: "{app}\Pexpo.exe"; Description: "Launch Pexpo Music"; Flags: nowait postinstall skipifsilent
