# 갤럭시용 스탠바이 기능 분석

조사일: 2026-10-23, 한국 시간<br>
대상 기기: Galaxy Z Fold6 / Android 16 / One UI 8.5 — 사용자가 알려준 환경이며 기기에 직접 연결해 확인하지는 않았다.<br>
범위: iPhone 기본 스탠바이, Zetabit StandBy Mode Pro, AdamWilkinson0/android-standby, Android 공개 API와 Fold6 대응 설계.

**결론**

개인용으로 충분히 만들 만하다. 시계·사진·위젯 화면은 구현 가능성이 높고, 먼저 증명해야 할 부분은 **충전 중 자동 진입, 잠금 상태 표시, 접기·펼치기 전환**이다. 권장 방향은 Kotlin + Jetpack Compose로 독립 구현하고, Fold6의 커버 화면과 반접힘 자세를 처음부터 지원하는 것이다. 이는 조사에 따른 설계 제안이며 구현·실기기 검증 결과가 아니다.

StandBy Mode Pro는 iOS 앱이 아니라 **iPhone 스탠바이를 Android로 옮긴 경쟁 앱**이다. 공식 홈페이지는 무료 기본 사용과 일회성 Premium 구매를 안내한다. 정확한 국내 가격과 기능별 결제 제한은 앱 내 구매 화면을 확인하지 못했다. [공식 홈페이지](https://zetabitapps.com/standbymodepro), [Google Play](https://play.google.com/store/apps/details?id=br.com.zetabit.ios_standby&hl=en)

아래에서 **공식 안내**는 제조사·개발사의 설명, **소스 확인**은 고정 커밋을 읽어 확인한 구현, **제안**은 이번 앱의 설계 판단을 뜻한다. 공식 안내만으로 해당 기능이 Fold6에서 정상 동작한다고 판정하지 않았다.

**1. iPhone 기본 스탠바이에서 가져올 경험**

| 기능 | 확인한 동작 | 제품에서 중요한 이유 |
| --- | --- | --- |
| 시작 조건 | 충전기에 연결하고 옆으로 고정한 뒤 측면 버튼으로 진입 | 사용자가 매번 앱을 찾아 열지 않아도 되는 경험 |
| 세 가지 보기 | 위젯, 사진, 전체 화면 시계 | 화면 구성을 복잡하게 만들지 않고 목적별로 전환 |
| 제스처 | 좌우로 보기 전환, 위아래로 해당 보기의 옵션 전환 | 멀리서 보는 큰 화면과 간단한 조작의 조합 |
| 야간 모드 | 어두운 환경에서 어둡고 붉은 표시 | 침대 옆에서 눈부심 감소 |
| 표시 시간 | 자동, 20초 후, 계속 표시 선택 | 항상 켜 두는 것과 필요할 때만 보는 것의 선택 |
| 화면 깨우기 | 모델에 따라 상시표시 또는 탭·작은 움직임·Siri 사용 | 기기별 화면 정책 차이 |
| 충전 위치별 기억 | MagSafe 충전 위치마다 선호 보기 기억 | 침실·책상 등 장소별 설정 |

위 표는 일반적인 바 형태 iPhone의 공식 사용 흐름을 기준으로 했다. 모델별 상시표시와 깨우기 동작은 동일하지 않다. [Apple 한국어 사용 설명서](https://support.apple.com/ko-kr/guide/iphone/iph878d77632/ios)

추가로 위젯의 Smart Stack, 실시간 현황, 음악 조작, 확대 알림, 수신 전화, Siri 결과가 연결된다. 핵심은 정보가 바뀔 때 그에 맞는 화면을 보여주는 것이다. Android에서는 음악·자체 타이머·선택한 알림으로 비슷한 경험을 만들 수 있지만, 다른 앱의 iOS Live Activities를 그대로 가져오는 기능은 아니다. [Apple 스탠바이 소개](https://www.apple.com/newsroom/2023/09/ios-17-is-available-today/), [Live Activities 표시 규칙](https://developer.apple.com/design/human-interface-guidelines/live-activities)

**2. StandBy Mode Pro에서 참고할 기능**

공식 홈페이지와 스토어의 개발자 설명을 함께 확인했다. 홈페이지는 2·4개 배치를 소개하고, 스토어 설명에는 2·3·4개 배치가 나온다. 아래는 해당 설명에서 묶어 정리한 기능이다.

| 분류 | 공식적으로 안내하는 기능 |
| --- | --- |
| 화면 | 100개 이상 시계, Duo/다중 위젯, 외부 앱 위젯, 사진 액자 |
| 생활 정보 | 날씨, 일정, 음악 제어·가사, 알림, 배터리·시스템 정보 |
| 자동화 | 충전 방식·Wi-Fi·시간·화면 꺼짐 조건, 잠금화면 위 표시, 충전 해제 시 종료, Android 화면보호기 |
| 야간·장시간 사용 | 시간/조도 기반 야간 모드, 주야간 밝기, 픽셀 이동, 화면 종료 타이머 |
| 확장 기능 | 집중 타이머, 카운트다운, 스톱워치, Spaces 설정 묶음, 라디오·영상 |

개발사가 제시한 기능이며 직접 실행해 검증한 목록은 아니다. 특히 **2026-10-18 갱신된 스토어의 변경 이력에 Android 15 이상 자동 실행 수정이 기록되어 있다.** 자동 실행은 상용 앱에서도 버전별 대응이 필요한 기능이라는 근거다. [Google Play 기능·변경 이력](https://play.google.com/store/apps/details?id=br.com.zetabit.ios_standby&hl=en)

홈페이지는 기본 시계·사진·음악 제어·픽셀 이동을 무료 기능으로, 고급 시계·실시간 날씨·추가 꾸미기·광고 제거를 Premium 예로 설명한다. 모든 개별 기능의 요금 구분은 확인되지 않았다. 사진 구성과 위젯 선택의 자유도는 참고할 만하지만, 첫 버전에 시계 수십 개와 부가 도구를 모두 넣을 필요는 없다. [공식 기능 및 Premium 안내](https://zetabitapps.com/standbymodepro)

**3. 세 대상의 차이**

| 항목 | iPhone 기본 | StandBy Mode Pro 공식 안내 | android-standby 소스 |
| --- | --- | --- | --- |
| 전체 시계 | 제공 | 다양한 시계 제공 | 4종 구현 |
| 나란히 보는 위젯 | 제공 | 다중 배치·외부 위젯 | 내장 패널 2개 또는 3개 |
| 사진 액자 | 제공 | 제공 | 구현 없음 |
| 날씨 | 위젯으로 제공 | 제공 | Open-Meteo 연동 |
| 음악 | Now Playing | 제어·가사 | MediaSession 표시·제어 |
| 일정 | 위젯으로 제공 | 제공 | 기기 일정, 다음 48시간 |
| 일반 알림 표시 | 제공 | 제공 | 구현 없음 |
| 충전 자동 진입 | 시스템 기능 | 제공한다고 안내 | 구현 없음 |
| 잠금 상태 위 표시 | 시스템 기능 | 제공한다고 안내 | 관련 설정·호출 없음 |
| 야간 전환 | 저조도 대응 | 시간/조도 | 22:00~07:00 고정 시간 |
| 화면보호기 연동 | 별도 앱 서비스가 아님 | 제공한다고 안내 | DreamService 없음 |
| 접힘 자세 전용 처리 | 대상 기기와 별개 | 폴더블 지원 문구, 세부 동작 미확인 | 관련 API 없음 |

iPhone과 상용 앱 열은 앞선 공식 자료에 근거한다. 공개 저장소 열은 아래 고정 커밋의 전체 Kotlin 파일과 Manifest·설정 파일을 조회한 결과다. “없음”은 **이 저장소의 해당 커밋에 구현이 없다는 뜻**이다.

**4. android-standby 공개 코드 분석**

분석 기준은 main의 `93f7ab96bd7bda44ccaa81b80a4acbdfb9c251b0`이다. 마지막 커밋은 2026-07-22의 README 변경이다. 재귀 파일 트리 78개 중 텍스트 67개를 조회했으며, Kotlin 56개를 모두 포함한다. 바이너리 폰트와 Gradle wrapper 등은 분석하지 않았다. [고정 커밋](https://github.com/AdamWilkinson0/android-standby/tree/93f7ab96bd7bda44ccaa81b80a4acbdfb9c251b0), [기계 판독용 조사 기록](./android-standby-source-audit.json)

| 관찰 | 소스 근거 | 판단 |
| --- | --- | --- |
| Kotlin + Compose, DataStore, ViewModel/Repository | [build.gradle.kts](https://github.com/AdamWilkinson0/android-standby/blob/93f7ab96bd7bda44ccaa81b80a4acbdfb9c251b0/app/build.gradle.kts#L8-L58) | Android 전용 구조 참고에 적합 |
| minSdk 26, targetSdk 35, compileSdk 36 | 위 빌드 설정 | Android 16에서 실행하는 것과 targetSdk 36 정책을 적용받는 것은 구별해야 함 |
| 가로 방향 지정, 일반 Launcher Activity | [Manifest](https://github.com/AdamWilkinson0/android-standby/blob/93f7ab96bd7bda44ccaa81b80a4acbdfb9c251b0/app/src/main/AndroidManifest.xml#L16-L25) | 가로형 앱 실행 구조 |
| 화면 유지, 시스템 바 숨김, 앱 창 밝기 조절 | [MainActivity](https://github.com/AdamWilkinson0/android-standby/blob/93f7ab96bd7bda44ccaa81b80a4acbdfb9c251b0/app/src/main/java/com/adamwilkinson/standby/MainActivity.kt#L41-L74) | 일반 앱 화면을 켜 두는 방식 |
| Digital / Analog / Flip / Stacked 시계 | [ClockFace](https://github.com/AdamWilkinson0/android-standby/blob/93f7ab96bd7bda44ccaa81b80a4acbdfb9c251b0/app/src/main/java/com/adamwilkinson/standby/ui/pages/clockfaces/ClockFace.kt#L14-L23) | 적은 종류로 시작할 때 유용한 범위 |
| 좌측 하나 + 우측 하나 또는 위아래 두 개 | [SplitView](https://github.com/AdamWilkinson0/android-standby/blob/93f7ab96bd7bda44ccaa81b80a4acbdfb9c251b0/app/src/main/java/com/adamwilkinson/standby/ui/split/SplitView.kt#L33-L64) | 최대 3패널이며 외부 홈 위젯 호스트는 아님 |
| 음악 시작 시 자동으로 음악 패널 표시 | [StandbyPagerScreen](https://github.com/AdamWilkinson0/android-standby/blob/93f7ab96bd7bda44ccaa81b80a4acbdfb9c251b0/app/src/main/java/com/adamwilkinson/standby/ui/StandbyPagerScreen.kt#L123-L169) | 최근 스와이프 후 3초 보호, 정지 10초 후 기존 패널 복구 의도 |
| 22시~7시 어두운 색 오버레이 | [야간 로직](https://github.com/AdamWilkinson0/android-standby/blob/93f7ab96bd7bda44ccaa81b80a4acbdfb9c251b0/app/src/main/java/com/adamwilkinson/standby/ui/StandbyPagerScreen.kt#L180-L186) | 조도 센서 기반 빨간색 테마와 다름 |
| 매분 최대 ±8px 이동, 2초 전환 | [BurnInDrift](https://github.com/AdamWilkinson0/android-standby/blob/93f7ab96bd7bda44ccaa81b80a4acbdfb9c251b0/app/src/main/java/com/adamwilkinson/standby/ui/BurnInDrift.kt#L23-L39) | 잔상 위험 완화 장치이며 번인 방지 보장은 아님 |

데이터 연동도 실제 코드가 있다.

- **음악:** 재생 중 세션을 우선 선택하고 제목·아티스트·앨범 그림을 읽으며 재생/정지·이전/다음을 호출한다. 알림 접근 권한을 세션 조회에 사용한다. 알림 리스너 자체는 비어 있어 메시지 목록을 구현한 것은 아니다. [MediaSessionRepository](https://github.com/AdamWilkinson0/android-standby/blob/93f7ab96bd7bda44ccaa81b80a4acbdfb9c251b0/app/src/main/java/com/adamwilkinson/standby/data/media/MediaSessionRepository.kt), [MediaNotificationListener](https://github.com/AdamWilkinson0/android-standby/blob/93f7ab96bd7bda44ccaa81b80a4acbdfb9c251b0/app/src/main/java/com/adamwilkinson/standby/data/media/MediaNotificationListener.kt)
- **날씨:** 수동 도시 또는 대략적 위치, 30분 기준 갱신과 로컬 캐시, 실패 시 이전 데이터 사용 경로가 있다. [WeatherRepository](https://github.com/AdamWilkinson0/android-standby/blob/93f7ab96bd7bda44ccaa81b80a4acbdfb9c251b0/app/src/main/java/com/adamwilkinson/standby/data/weather/WeatherRepository.kt)
- **일정:** CalendarContract.Instances로 반복 일정을 포함한 다음 48시간, 최대 10개를 조회한다. [CalendarRepository](https://github.com/AdamWilkinson0/android-standby/blob/93f7ab96bd7bda44ccaa81b80a4acbdfb9c251b0/app/src/main/java/com/adamwilkinson/standby/data/calendar/CalendarRepository.kt#L27-L65)
- **배터리:** 앱 안에서 ACTION_BATTERY_CHANGED를 구독해 잔량·충전 종류를 표시한다. 여기서 Activity를 시작하지는 않는다. [BatteryRepository](https://github.com/AdamWilkinson0/android-standby/blob/93f7ab96bd7bda44ccaa81b80a4acbdfb9c251b0/app/src/main/java/com/adamwilkinson/standby/data/battery/BatteryRepository.kt#L21-L38)
- **설정:** 시계·색·밝기·패널·도시 등을 DataStore에 저장한다. [SettingsRepository](https://github.com/AdamWilkinson0/android-standby/blob/93f7ab96bd7bda44ccaa81b80a4acbdfb9c251b0/app/src/main/java/com/adamwilkinson/standby/data/settings/SettingsRepository.kt)

원하는 앱과의 가장 큰 간격은 **실행을 관리하는 부분**이다. 충전 연결/해제에 따른 시작·종료, DreamService, 잠금 위 표시, 재부팅 복구, 외부 AppWidgetHost, 접힘 상태 처리를 찾지 못했다. README의 충전·가로 사용 설명과 자동 실행 구현을 동일하게 해석하면 안 된다.

또한 조회 시점에 GitHub Releases는 비어 있었고, 파일 트리에 테스트 경로와 LICENSE/COPYING/NOTICE 파일이 없었으며 GitHub의 license 필드는 null이었다. 따라서 재사용 허용 범위가 확인된 오픈소스로 단정하지 않는다. **구조를 참고하고 코드는 독립 구현하는 방향을 권한다.** 빌드와 앱 설치는 실행하지 않았다. [저장소 메타데이터](https://api.github.com/repos/AdamWilkinson0/android-standby), [Releases](https://github.com/AdamWilkinson0/android-standby/releases)

**5. Fold6에서 추가로 필요한 설계**

| 사용 자세 | 제안 화면 | 확인할 점 |
| --- | --- | --- |
| 닫은 상태, 커버 화면 가로 거치 | 시계 + 선택 위젯 2분할 | 좁은 높이, 글자 잘림, 충전·잠금 진입 |
| 펼친 내부 화면 | 큰 시계 또는 넓은 2분할 | 거의 정사각형인 공간을 단순 확대하지 않기 |
| 반접힘 + 수평 힌지, 탁상 자세 | 위쪽에 시계/정보, 아래쪽에 조작부 | 접힌 선을 가로질러 핵심 정보가 배치되지 않게 하기 |
| 반접힘 + 수직 힌지, 책처럼 든 자세 | 일반 앱 화면 유지 | 반접혔다는 이유만으로 스탠바이 자동 진입하지 않기 |

위 내용은 Fold6용 제안이다. Android는 HALF_OPENED 상태에서 힌지 방향에 따라 tabletop과 book 자세를 구분한다. WindowManager의 접힘 정보와 현재 창 크기를 함께 사용해야 한다. [Android 폴더블 가이드](https://developer.android.com/develop/adaptive-apps/guides/foldables/learn-about-foldables)

**Android 16에서 주의할 정확한 조건:** targetSdk 36 이상인 앱이 sw600dp 이상의 큰 화면에서 실행되면 방향·화면비·크기 제한이 무시될 수 있다. 내부 화면은 이 정책을 고려해야 하며, 커버 화면까지 무조건 같은 정책이라고 가정하면 안 된다. 참고 저장소는 targetSdk 35이므로 이 정책만으로 현재 앱이 깨진다고 단정할 수는 없다. 새 앱은 고정 sensorLandscape 의존을 피하고 창 크기에 맞춰 재배치하도록 제안한다. [Android 방향·크기 정책](https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability)

또한 Fold6에는 ‘커버 화면에서 앱 계속 사용’ 설정이 있다. 접을 때 앱이 이어지는지, 화면이 꺼지는지는 이 설정과 함께 확인해야 한다. 실제 One UI 8.5의 메뉴 및 동작은 아직 확인하지 않았다. [Samsung Fold6 커버 화면 안내](https://www.samsung.com/us/support/answer/ANS10003467/)

**6. 시스템 연동: 가능한 것과 먼저 검증할 것**

| 구현 방식 | 장점 | 한계·검증 지점 |
| --- | --- | --- |
| 일반 Activity + 수동 시작 | 화면과 데이터 연동을 쉽게 검증 | 충전만으로 자동 시작되는 것은 아님 |
| DreamService 화면보호기 | 충전 중 유휴/도크 상태에 연결하는 공식 경로 | 가로로 놓는 즉시 실행과는 다름. Fold6 설정·잠금·화면 꺼짐 조건 확인 |
| 앱 자체 자동 진입 관리 | 충전·자세·시간 조건을 세밀하게 구성 가능 | 백그라운드 Activity 시작 제한, 서비스 시작 제한, 프로세스 종료 대응 필요 |

**권장 순서:** 수동 실행 화면으로 기본 표시를 증명하고, DreamService 경로를 먼저 시험한다. 원하는 즉시 진입을 충족하지 못하면 허용된 별도 진입 경로를 비교한다. 충전 BroadcastReceiver에서 startActivity를 호출하면 해결된다고 전제하지 않는다. [DreamService](https://developer.android.com/reference/android/service/dreams/DreamService), [백그라운드 Activity 시작 제한](https://developer.android.com/guide/components/activities/secure-bal)

Foreground Service를 쓴다고 임의의 화면 실행이 보장되지 않는다. Android 15 이상을 대상으로 할 때 SYSTEM_ALERT_WINDOW 예외로 백그라운드에서 Foreground Service를 시작하려면 보이는 오버레이 등의 조건도 확인해야 한다. 이는 **Activity 시작 권한과 별개의 서비스 시작 조건**이다. [Foreground Service 변경사항](https://developer.android.com/develop/background-work/services/fgs/changes)

잠금 위 화면 표시는 setShowWhenLocked, 화면 켜기는 setTurnScreenOn을 검토할 수 있다. 이 API가 앱 실행 제한을 없애거나 보안 잠금을 해제하는 것은 아니다. 사진 선택·일정 세부 정보·외부 앱 열기처럼 개인 데이터에 접근하는 흐름은 잠금 상태 정책을 별도로 정해야 한다. [Activity API](https://developer.android.com/reference/android/app/Activity#setShowWhenLocked(boolean)), [KeyguardManager](https://developer.android.com/reference/android/app/KeyguardManager#requestDismissKeyguard(android.app.Activity,android.app.KeyguardManager.KeyguardDismissCallback))

일반 앱의 화면 유지와 삼성 기본 AOD의 전력 특성은 같은 것으로 취급하지 않는다. FLAG_KEEP_SCREEN_ON은 앱 화면이 보일 때 동작하며 백그라운드로 가면 일반 화면 꺼짐 정책을 따른다. 번인·발열·소비 전력은 밝기, 표시 면적, 갱신 주기와 함께 실측해야 한다. [화면 유지 API](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on)

추가 데이터·권한 설계는 다음과 같다.

| 기능 | 제안 |
| --- | --- |
| 시계·날짜·배터리 | 초기 기본 화면. 불필요한 권한 요청 없이 시작 |
| 사진 | 시스템 Photo Picker로 사용자가 선택한 사진만 접근, 재실행 후 URI 접근도 검증 |
| 음악 | 사용자 알림 접근 허용 후 MediaSession 연동. 앱별 제공 범위 확인 |
| 일정 | READ_CALENDAR를 일정 추가 시 요청. 거절 시 다른 패널 사용 |
| 날씨 | 수동 도시를 기본 대안으로 제공하고 필요할 때 대략적 위치 요청 |
| 외부 위젯 | AppWidgetHost와 사용자 바인딩 동의, 크기·갱신·탭 동작 검증 |
| 일반 알림 | 음악 권한 획득과 표시 동의를 구분. 앱별 허용·내용 가리기 설계 |
| 충전 조건 | 전원 연결과 실제 충전 중 상태를 구별. 완충·배터리 보호로 충전이 멈췄을 때도 의도대로 유지 |

Photo Picker는 전체 갤러리 권한 대신 선택한 사진을 제공하고, 외부 위젯은 사용자 바인딩 승인이 필요할 수 있다. “어떤 위젯이든” 같은 호환성 약속은 실제 검증 범위 안에서만 해야 한다. [Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker), [AppWidgetHost](https://developer.android.com/develop/ui/views/appwidgets/host)

날씨는 개인용 시제품에 Open-Meteo를 사용할 수 있다. 무료 API는 비상업적 사용 범위와 호출 제한이 있으므로 배포·수익화 범위가 바뀌면 조건을 다시 확인해야 한다. [Open-Meteo 요금·이용 범위](https://open-meteo.com/en/pricing)

**7. 권장 첫 범위와 구현 가능성**

첫 범위를 기준으로 한 잠정 점수는 **76/100, 범위를 제한해 구현 가능**이다. 통과율이나 테스트 수치가 아니라 기획 판단이며, 신뢰도는 중간이다.

| 기준 | 점수 | 근거 |
| --- | ---: | --- |
| 공개 동작의 명확성 | 14/15 | 공식 기능과 공개 소스가 있음 |
| 기술 접근성 | 13/20 | 공개 API는 있으나 자동 진입과 잠금 통합에 제약 |
| 개인용 범위 적합성 | 15/15 | 계정·서버 없이 유용한 기능 구성 가능 |
| 검증 가능성 | 12/20 | 대상 기기는 확인했지만 연결·실행 증거 없음 |
| 데이터·자산 확보 | 9/10 | 자체 시계·사용자 사진·공개 날씨 API 활용 가능 |
| 복잡도 관리 | 7/10 | Fold 자세·잠금·충전의 조합 관리 필요 |
| 코드·자산 재사용 조건 | 6/10 | 참고 저장소 라이선스 불명확, 독립 구현 전제 |

**Core — 첫 유용한 버전**

1. 전체 시계 2~3종, 시계+날짜/배터리의 2분할 화면.
2. 선택한 사진으로 액자 보기, 좌우 스와이프로 보기 전환.
3. 수동 시작과 화면보호기 연동. 즉시 자동 진입은 첫 기술 검증 결과로 범위 확정.
4. 야간 색·밝기, 검은 배경, 미세 이동, 갱신 빈도 절감.
5. Fold6 커버·내부·tabletop 레이아웃, 전환 후 설정 유지.
6. 충전 해제 시 종료 또는 화면 유지 해제 정책.

**Absorb — 다음으로 가져올 장점**

음악 제어, 날씨, 다음 일정, 패널 순서 저장, 조도 기반 야간 전환, 침대/책상 프리셋. 외부 앱 위젯은 내장 패널이 안정화된 다음 추가한다.

**Later — 첫 버전 이후 판단**

일반 알림 전체 표시, 가사, 다양한 시계 테마, 4분할, 라디오·영상, 클라우드 사진 자동 연동, 장소·충전기별 자동 프로필. MagSafe 충전기 식별 경험을 Android에서 그대로 재현할 수 있다고 가정하지 않고 수동 프로필부터 시작한다.

Android 시스템 연동과 Fold6 자세 처리가 핵심이므로 **Kotlin + Jetpack Compose + Jetpack WindowManager + DataStore**를 권한다. 화면만 보이는 웹 프로토타입은 레이아웃 비교에는 쓸 수 있지만, 자동 진입·잠금·전력 검증을 대신하지 못한다. 이번에는 기능 분석까지만 수행했고 앱 코드를 생성하지 않았다.

**8. 구현을 시작할 때의 첫 기술 검증**

다음은 실행 결과가 아니라 제안하는 검증 기준이다. 먼저 시계 하나와 진단 화면만 있는 작은 앱으로 확인한다.

| 순서 | 확인할 상황 | 성공 증거 |
| --- | --- | --- |
| 1 | 수동 실행 → 가로 화면 유지 | 화면·밝기·종료를 실제 기기에서 확인 |
| 2 | 화면보호기 선택 → 충전 중 유휴 | DreamService 시작/종료 로그와 실제 화면 일치 |
| 3 | 잠금·화면 꺼짐 상태의 충전 연결 | 선택한 진입 경로가 실제 표시까지 이어짐 |
| 4 | 유선·무선·완충·배터리 보호 | 충전 조건 오판으로 반복 진입/종료하지 않음 |
| 5 | 충전 해제·기기 집어 들기 | 합의한 종료 정책대로 복귀 |
| 6 | 커버 → 펼침 → tabletop → 닫기 | 선택 화면·글꼴·밝기 유지, 힌지 부근 정보 잘림 없음 |
| 7 | book 자세·일반 앱 사용 중 | 원하지 않는 스탠바이 전환 없음 |
| 8 | 권한 거절·회수 | 크래시 없이 기본 시계 사용 가능 |
| 9 | 프로세스 종료·재부팅·강제 종료 | 재진입 가능 범위와 수동 재개가 필요한 상태를 정확히 구분 |
| 10 | 30분 예비 사용 → 실제 야간 사용 | 온도·배터리·화면 유지 관찰 기록. 장기 번인 안전 보장으로 확대하지 않음 |

로그에는 전원 연결 종류, 실제 충전 상태, 잠금·화면 상태, 창 크기, 접힘 자세, 진입 시도 경로, 성공/차단 사유를 남긴다. 충전·자세 조합마다 몇 차례 반복하고, 자동 진입 실패는 화면보호기 진입 실패인지 Activity 실행 차단인지 구분한다.

자동 진입을 증명하지 못하면 그 상태를 먼저 해결하거나 수동/화면보호기 범위로 조정한다. UI 테마를 늘리는 것으로 핵심 동작의 미검증 상태를 덮지 않는다.

**9. REA 적용 범위와 이번 조사 한계**

REA는 Reverse Engineer Anything으로 해석하고 공식 소개를 확인했다. APK 분석에 도움이 될 수 있지만 현재 세션에는 REA 도구가 노출되어 있지 않으며, StandBy Mode Pro의 APK도 제공되지 않았다. 이번에는 REA 설치·APK 디컴파일·런타임 후킹을 수행하지 않았다. [REA 공식 소개](https://rea.tools/)

추가 분석을 한다면 범위를 다음 세 질문으로 좁히는 것이 유용하다.

1. 자동 실행은 DreamService, Receiver, Service, Activity 중 어떤 경로로 연결되는가?
2. 야간 전환은 조도·시간·밝기를 어떻게 조합하고 어떤 권한을 요구하는가?
3. Fold6의 커버/내부/반접힘에서 레이아웃과 화면 유지가 어떻게 달라지는가?

이때 공식 설치본의 패키지·버전·APK 해시를 먼저 고정하고, Manifest와 정적 호출 경로를 확인한 뒤 실제 기기 로그·관찰과 대조해야 한다. 권한 선언만으로 동작을 확정하면 안 된다.

| 항목 | 이번 상태 |
| --- | --- |
| Apple 공식 기능 확인 | 완료 |
| StandBy Mode Pro 공식 기능·과금 방식 확인 | 완료, 정확한 국내 가격·개별 제한 미확인 |
| android-standby Manifest·Kotlin·설정 정적 조사 | 완료, 고정 커밋 기준 |
| Android/Fold6 공식 제약 조사 | 완료, 현재 기기 동작과 구별 |
| REA·APK 바이너리 분석 | 미실행 |
| 오픈소스 빌드·설치·테스트 | 미실행 |
| Fold6 자동 실행·잠금·전력 검증 | 미실행 |
| 앱 구현 | 미착수, 분석 문서만 작성 |
