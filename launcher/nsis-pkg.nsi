; ============================================================
;  kt-gui NSIS 安装打包脚本
;  项目: KT GUI (JavaFX 17, io.github.devinx3.kt.Launcher)
;  版本: 0.2
;
;  构建步骤:
;   1) 生成自包含应用镜像 target/app/kt-gui (二选一):
;      a. 修改 pom.xml 中 jpackage-maven-plugin 的 <type> 为 APP_IMAGE,
;         然后执行: mvn clean package javafx:jlink jpackage:jpackage
;      b. 或手动执行:
;         jpackage --name kt-gui --app-version 0.2 --vendor devinx3 \
;                  --module io.github.devinx3.kt/io.github.devinx3.kt.Launcher \
;                  --runtime-image target/image --type app-image \
;                  --dest target/app --icon public/favicon.ico
;   2) makensis kt-gui.nsi
;   输出: dist/KT-GUI-Setup-0.2.exe
;
;  注意: 本文件必须保存为 "UTF-8 带 BOM" 编码 (中文界面字符串依赖它)。
; ============================================================

Unicode true
!include "x64.nsh"

; ---------------- 常量 ----------------
!define APP_NAME      "KT GUI"
!define APP_EXE       "kt-gui.exe"
!define APP_VERSION   "0.2"
!define APP_PUBLISHER "devinx3"
!define APP_ID        "kt-gui"
!define APP_SRC       "target\app\kt-gui"          ; jpackage 应用镜像目录
!define APP_ICON      "public\favicon.ico"
!define OUT_FILE      "dist\KT-GUI-Setup-${APP_VERSION}.exe"
!define UNINST_KEY    "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_ID}"

; ---------------- 安装包元信息 ----------------
Name "${APP_NAME}"
OutFile "${OUT_FILE}"
InstallDir "$PROGRAMFILES64\${APP_NAME}"
InstallDirRegKey HKLM "${UNINST_KEY}" "InstallLocation"
RequestExecutionLevel admin
SetCompressor /SOLID lzma
VIProductVersion "0.2.0.0"
VIAddVersionKey "ProductName"     "${APP_NAME}"
VIAddVersionKey "ProductVersion"  "${APP_VERSION}"
VIAddVersionKey "FileDescription" "${APP_NAME} 安装程序"
VIAddVersionKey "FileVersion"     "${APP_VERSION}.0.0"
VIAddVersionKey "CompanyName"     "${APP_PUBLISHER}"
VIAddVersionKey "LegalCopyright"  "Copyright (C) 2026 ${APP_PUBLISHER}"

!ifdef APP_ICON
  !define MUI_ICON   "${APP_ICON}"
  !define MUI_UNICON "${APP_ICON}"
!endif

; ---------------- MUI 2 ----------------
!include "MUI2.nsh"

!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\${APP_EXE}"

; 安装页面
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

; 卸载页面
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

; 语言 (第一个为默认语言)
!insertmacro MUI_LANGUAGE "SimpChinese"
!insertmacro MUI_LANGUAGE "English"

; ---------------- 自定义提示文本 ----------------
LangString AppRunningMsg ${LANG_SIMPCHINESE} "检测到 ${APP_NAME} 正在运行。$\r$\n安装前需要先关闭它，是否立即关闭？"
LangString AppRunningMsg ${LANG_ENGLISH} "${APP_NAME} is currently running.$\r$\nIt must be closed before continuing. Close it now?"
LangString NeedAdminMsg  ${LANG_SIMPCHINESE} "需要管理员权限才能安装 ${APP_NAME}，请以管理员身份运行安装程序。"
LangString NeedAdminMsg  ${LANG_ENGLISH} "Administrator privileges are required to install ${APP_NAME}. Please run the installer as administrator."

; ---------------- 通用函数: 结束运行中的程序 ----------------
!macro CloseRunningApp
  ; tasklist 过滤到 kt-gui.exe: 找到时返回码为 0, 未找到为 1
  nsExec::ExecToStack 'tasklist /fi "IMAGENAME eq ${APP_EXE}" /fo csv /nh'
  Pop $0 ; 返回码
  Pop $1 ; 输出
  ${If} $0 == 0
    MessageBox MB_YESNO|MB_ICONQUESTION "$(AppRunningMsg)" IDYES closeApp IDNO giveUp
    closeApp:
      nsExec::ExecToLog 'taskkill /f /im ${APP_EXE}'
      Sleep 500
      Goto done
    giveUp:
      Abort
    done:
  ${EndIf}
!macroend

; ---------------- 安装 ----------------
Section "${APP_NAME}" SecMain
  ; 防止直接装到 Program Files 根目录 / 盘符根目录
  ${If} $INSTDIR == "$PROGRAMFILES64"
    Abort
  ${EndIf}

  SetOutPath "$INSTDIR"
  ; 先清掉旧版本残留, 再写入新镜像
  RMDir /r "$INSTDIR"
  File /r "${APP_SRC}\*"

  ; 卸载程序
  WriteUninstaller "$INSTDIR\Uninstall.exe"

  ; 注册表: 添加/删除程序
  WriteRegStr   HKLM "${UNINST_KEY}" "DisplayName"     "${APP_NAME}"
  WriteRegStr   HKLM "${UNINST_KEY}" "DisplayVersion"  "${APP_VERSION}"
  WriteRegStr   HKLM "${UNINST_KEY}" "Publisher"       "${APP_PUBLISHER}"
  WriteRegStr   HKLM "${UNINST_KEY}" "DisplayIcon"     "$INSTDIR\${APP_EXE}"
  WriteRegStr   HKLM "${UNINST_KEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegStr   HKLM "${UNINST_KEY}" "InstallLocation" "$INSTDIR"
  WriteRegDWORD HKLM "${UNINST_KEY}" "NoModify"        1
  WriteRegDWORD HKLM "${UNINST_KEY}" "NoRepair"        1

  ; 应用启动时始终以管理员身份运行 (UAC 提权)
  WriteRegStr HKLM "Software\Microsoft\Windows NT\CurrentVersion\AppCompatFlags\Layers" "$INSTDIR\${APP_EXE}" "RunAsAdmin"

  ; 开始菜单 + 桌面快捷方式
  CreateDirectory "$SMPROGRAMS\${APP_NAME}"
  CreateShortcut "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk" "$INSTDIR\${APP_EXE}"
  CreateShortcut "$DESKTOP\${APP_NAME}.lnk"                 "$INSTDIR\${APP_EXE}"
SectionEnd

; ---------------- 卸载 ----------------
Section "Uninstall"
  ; 结束运行中的程序
  nsExec::ExecToLog 'taskkill /f /im ${APP_EXE}'
  Sleep 500

  ; 删除快捷方式
  Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk"
  RMDir  "$SMPROGRAMS\${APP_NAME}"
  Delete "$DESKTOP\${APP_NAME}.lnk"

  ; 删除注册表
  DeleteRegKey HKLM "${UNINST_KEY}"
  DeleteRegValue HKLM "Software\Microsoft\Windows NT\CurrentVersion\AppCompatFlags\Layers" "$INSTDIR\${APP_EXE}"

  ; 删除安装目录
  RMDir /r "$INSTDIR"
SectionEnd

; ---------------- 安装前检查 ----------------
Function .onInit
  SetRegView 64
  ${IfNot} ${RunningX64}
    MessageBox MB_OK|MB_ICONSTOP "${APP_NAME} 需要 64 位 Windows 系统。"
    Quit
  ${EndIf}

  UserInfo::GetAccountType
  Pop $0
  ${If} $0 != "admin"
    MessageBox MB_OK|MB_ICONSTOP "$(NeedAdminMsg)"
    Quit
  ${EndIf}

  ; 若程序正在运行则先关闭
  !insertmacro CloseRunningApp
FunctionEnd

; ---------------- 卸载前检查 ----------------
Function un.onInit
  SetRegView 64
  ; 若程序正在运行则先关闭
  !insertmacro CloseRunningApp
FunctionEnd
