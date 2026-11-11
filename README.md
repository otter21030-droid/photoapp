# 프레임핏 (FrameFit) — 안드로이드 웹뷰 앱

> **웹사이트:** [https://otter21030-droid.github.io/newapp/](https://otter21030-droid.github.io/newapp/)  
> 사진/동영상/PDF 비율 맞춤 및 편집 서비스를 위한 네이티브 안드로이드 전체화면 앱입니다.

---

## 🌟 주요 개선 및 탑재 기능

### 1. 모바일 최적화 화면 (PC 데스크톱 크기 문제 해결)
- 기존 웹사이트의 `min-width: 1120px` 데스크톱 전용 가로폭 제한을 해제하고, 모바일 세로 화면(360px~480px)에 최적화된 **반응형 모바일 CSS를 동적 주입**합니다.
- 모바일 뷰포트(`<meta name="viewport">`) 자동 보정 및 텍스트/버튼 크기를 모바일 터치 환경에 최적화했습니다.
- 상단 파일 포맷 바는 매끄러운 가로 스크롤로 전환되고, 파일 업로드 영역 및 제어판이 한 손 조작에 맞게 카드형으로 자동 정렬됩니다.

### 2. 저장공간 내 원하는 위치에 다운로드 (Storage Access Framework: SAF)
- 웹사이트에서 사진, 동영상, 구간별 편집본, 일괄 ZIP 압축 파일을 다운로드할 때 **안드로이드 시스템의 파일 저장 위치 선택 창(SAF: 다른 이름으로 저장)**이 열립니다.
- 사용자가 원하는 폴더(다운로드, 내 파일, SD 카드, 특정 폴더 등)를 직접 선택하고 파일 이름을 지정하여 안전하게 저장할 수 있습니다.
- 대용량 동영상 및 ZIP 파일도 안전하게 저장할 수 있도록 청크(Chunk) 스트리밍 저장 메커니즘을 적용했습니다.

### 3. 사진, 동영상, PDF 파일 완벽 지원
- 파일 선택 창(`onShowFileChooser`)에서 `image/*`뿐만 아니라 **동영상(`video/*`)**과 **PDF 문서(`application/pdf`)**, **ZIP 압축 파일**까지 모두 선택하여 작업할 수 있습니다.
- **카메라 촬영(사진 촬영)**뿐만 아니라 **캠코더(동영상 녹화)** 옵션까지 시스템 Chooser에 통합 제공합니다.
- Android 13+ 최신 권한(`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`) 및 마이크/카메라 권한을 완벽하게 처리합니다.
- 동영상 편집 및 감상을 위한 **하드웨어 가속** 및 **비디오 전체화면(HTML5 Fullscreen Video)** 재생을 지원합니다.

### 4. 세련된 앱 아이콘 및 기본 네이티브 편의 기능
- **Adaptive Icon**: 선셋 코랄 핑크 & 일렉트릭 바이올렛 & 오션 블루 3색 그라데이션, 황금비율 크롭 가이드(Viewfinder), 카메라 렌즈 및 골든 스파클 디자인 적용.
- **몰입형 전체화면 (Immersive Sticky Fullscreen)**: 상태바와 내비게이션바를 숨겨 화면을 100% 넓게 활용.
- **스마트 뒤로가기**: 웹 히스토리 뒤로가기 및 2초 내 재터치 시 앱 종료.
- **당겨서 새로고침**: 화면 최상단에서 아래로 당겨 새로고침.

---

## 🚀 GitHub Actions로 APK 빌드 및 다운로드 방법

로컬 컴퓨터에 안드로이드 스튜디오를 설치할 필요 없이, **GitHub에 올리기만 하면 클라우드에서 자동으로 APK를 빌드**합니다.

### 1단계: GitHub 저장소에 코드 업로드

터미널(PowerShell 또는 명령 프롬프트)에서 프로젝트 폴더로 이동 후 아래 명령어를 실행합니다:

```bash
cd C:\Users\Hss\.gemini\antigravity\scratch\framefit-android

# 변경사항 커밋
git add .
git commit -m "fix: mobile responsive ui, SAF download to custom location, video/pdf support"

# 원격 저장소 연결 (본인의 GitHub 저장소 URL로 변경)
git branch -M main
git remote add origin https://github.com/<YOUR_GITHUB_USERNAME>/framefit-android.git
git push -u origin main
```

*(이미 push한 적이 있다면 `git push origin main`만 입력하시면 됩니다.)*

### 2단계: GitHub Actions에서 빌드 확인 및 APK 다운로드

1. GitHub 저장소 상단의 **[Actions]** 탭을 클릭합니다.
2. `Build Android APK` 워크플로우가 자동으로 실행되는 것을 확인합니다.
3. 약 2~3분 후 빌드가 완료(초록색 체크 마크 ✔)되면 해당 실행 내역을 클릭합니다.
4. 페이지 하단의 **Artifacts** 섹션에 있는 **`FrameFit-APK`** 를 클릭하여 다운로드합니다.
5. 다운로드한 압축 파일 내의 `FrameFit-debug.apk`를 안드로이드 폰으로 옮겨 설치하시면 됩니다!

---

## 📁 주요 파일 구성

* [MainActivity.kt](file:///C:/Users/Hss/.gemini/antigravity/scratch/framefit-android/app/src/main/java/com/framefit/app/MainActivity.kt) : 모바일 반응형 CSS 주입, SAF 저장 다이얼로그 연동, 영상/PDF/이미지 파일 선택기, 비디오 전체화면
* [WebAppInterface.kt](file:///C:/Users/Hss/.gemini/antigravity/scratch/framefit-android/app/src/main/java/com/framefit/app/WebAppInterface.kt) : 자바스크립트-네이티브 SAF 파일 저장 브릿지 (청크 스트리밍 지원)
* [FileUtils.kt](file:///C:/Users/Hss/.gemini/antigravity/scratch/framefit-android/app/src/main/java/com/framefit/app/FileUtils.kt) : SAF 파일 쓰기 스트림 유틸 및 임시 파일 생성
* [AndroidManifest.xml](file:///C:/Users/Hss/.gemini/antigravity/scratch/framefit-android/app/src/main/AndroidManifest.xml) : 비디오/오디오/미디어 권한 및 하드웨어 가속 설정
* [build-apk.yml](file:///C:/Users/Hss/.gemini/antigravity/scratch/framefit-android/.github/workflows/build-apk.yml) : GitHub Actions 클라우드 빌드 스크립트
