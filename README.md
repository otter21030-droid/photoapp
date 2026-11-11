# 프레임핏 (FrameFit) — 안드로이드 웹뷰 앱

> **웹사이트:** [https://otter21030-droid.github.io/newapp/](https://otter21030-droid.github.io/newapp/)  
> 사진 비율 맞춤 및 편집 서비스를 위한 네이티브 안드로이드 전체화면 래퍼 앱입니다.

---

## 🌟 주요 기능 및 특징

1. **몰입형 전체화면 (Immersive Fullscreen)**
   - 상단 상태바(Status bar)와 하단 내비게이션바(Navigation bar)를 숨기고 100% 화면을 활용하는 전체화면 몰입 모드 지원
   - 화면 가장자리 스와이프 시 일시적으로 시스템 바 표시 (Sticky Immersive Mode)

2. **사진 편집 전용 파일 선택 & 카메라 연동 (`onShowFileChooser`)**
   - 웹사이트 내 `<input type="file">` 클릭 시 갤러리/파일 탐색기 선택 및 카메라 촬영 옵션을 제공하는 시스템 인텐트 Chooser 연동
   - 다중 이미지 선택 지원 및 안전한 취소 처리

3. **고급 다운로드 지원 (Blob & Base64 인터페이스)**
   - 웹앱에서 캔버스/ZIP/이미지를 내보낼 때 사용하는 `blob:` URL 및 `data:` URI를 감지하여 네이티브에서 기기의 **다운로드(Downloads)** 폴더에 저장
   - 일반 HTTP/HTTPS 다운로드도 안드로이드 시스템 `DownloadManager`로 원활하게 처리

4. **네이티브 UX 및 안정성**
   - **당겨서 새로고침 (Swipe Refresh)**: 최상단에서 아래로 당겨 페이지 새로고침
   - **스마트 뒤로가기**: 웹뷰 내 방문 기록이 있으면 뒤로가기, 최상위 화면에서는 2초 내 두 번 터치 시 앱 종료
   - **오프라인 및 에러 처리**: 네트워크 연결 실패 시 직관적인 에러 화면과 재시도(Retry) 버튼 제공
   - **모던 스플래시 화면**: 첫 로딩 시 브랜드 로고와 함께 부드러운 로딩 화면 표시 후 자동 전환

5. **밝고 세련된 앱 아이콘**
   - 사진 편집, 황금비율 크롭 가이드(Viewfinder), 마법 같은 보정 스파클을 상징하는 모던한 그라데이션 아이콘 적용
   - Android 8.0+ 최신 규격인 **Adaptive Icon** (Vector XML) 및 전 해상도 mipmap PNG, Google Play 스토어용 512x512 고해상도 아이콘 포함

---

## 🚀 GitHub Actions로 APK 빌드 및 다운로드 방법

로컬 컴퓨터에 안드로이드 스튜디오를 설치할 필요 없이, **GitHub에 올리기만 하면 클라우드에서 자동으로 APK를 빌드**합니다.

### 1단계: GitHub 저장소에 코드 업로드

1. [GitHub](https://github.com)에서 새로운 Repository를 생성합니다 (예: `framefit-android`).
2. 터미널(PowerShell 또는 명령 프롬프트)에서 프로젝트 폴더로 이동 후 아래 명령어를 실행합니다:

```bash
cd C:\Users\Hss\.gemini\antigravity\scratch\framefit-android

# Git 초기화 및 커밋
git init
git add .
git commit -m "feat: Initial commit for FrameFit WebView Android App"

# 원격 저장소 연결 (본인의 GitHub 저장소 URL로 변경)
git branch -M main
git remote add origin https://github.com/<YOUR_GITHUB_USERNAME>/framefit-android.git
git push -u origin main
```

### 2단계: GitHub Actions에서 빌드 확인 및 APK 다운로드

1. GitHub 저장소 상단의 **[Actions]** 탭을 클릭합니다.
2. `Build Android APK` 워크플로우가 자동으로 실행되는 것을 볼 수 있습니다. (수동으로 실행하고 싶다면 좌측 `Build Android APK` 선택 후 **Run workflow** 클릭)
3. 약 2~3분 후 빌드가 완료(초록색 체크 마크 ✔)되면 해당 실행 내역을 클릭합니다.
4. 페이지 하단의 **Artifacts** 섹션에 있는 **`FrameFit-APK`** 를 클릭하여 다운로드합니다.
5. 압축을 풀면 스마트폰에 즉시 설치 가능한 `FrameFit-debug.apk` 및 `FrameFit-release.apk`가 들어있습니다!

### 3단계: 스마트폰에 설치

- 다운로드한 APK 파일을 안드로이드 스마트폰으로 전송(카카오톡, 구글 드라이브, 케이블 연결 등) 후 설치하시면 됩니다.
- (첫 설치 시 '출처를 알 수 없는 앱 설치' 허용 필요)

---

## 📁 프로젝트 구조

```
framefit-android/
├── .github/
│   └── workflows/
│       └── build-apk.yml          # GitHub Actions 자동 빌드 스크립트
├── .gitignore
├── README.md
├── build.gradle.kts               # 프로젝트 수준 Gradle 설정
├── settings.gradle.kts
├── gradle.properties
├── gradlew, gradlew.bat           # Gradle Wrapper 실행 스크립트
├── gradle/wrapper/
│   ├── gradle-wrapper.jar         # Gradle 바이너리
│   └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts           # 모듈 수준 Gradle 설정 (종속성, SDK 버전 등)
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml    # 권한(인터넷, 카메라, 저장소) 및 액티비티 설정
        ├── ic_launcher-playstore.png # 512x512 고해상도 앱 아이콘
        ├── java/com/framefit/app/
        │   ├── MainActivity.kt    # 전체화면, 웹뷰, 파일 선택기, 뒤로가기
        │   ├── WebAppInterface.kt # Blob/Base64 다운로드 JavaScript 인터페이스
        │   └── FileUtils.kt       # 파일 저장 및 임시 파일 생성 유틸리티
        └── res/
            ├── drawable/          # 아이콘 벡터 및 스플래시 리소스
            ├── layout/            # activity_main.xml (웹뷰, 스플래시, 에러 뷰)
            ├── mipmap-*/          # 전 해상도별 앱 아이콘 (Adaptive & PNG)
            ├── values/            # strings.xml, colors.xml, themes.xml
            └── xml/               # file_paths.xml (FileProvider 설정)
```

