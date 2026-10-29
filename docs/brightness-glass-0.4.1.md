# 밝기 슬라이더 유리 표면 수정 — 0.4.1

2026-10-26 / Galaxy Z Fold6 `SM-F956N` / Android 16.

## 변경

기존 슬라이더는 위쪽 흰색과 아래쪽 청록색을 섞은 그라데이션 표면이었다. 배경 블러가 없어 사진 위에서도 실제 유리 재질처럼 보이지 않았다.

- 표면·테두리의 위아래 그라데이션과 트랙의 민트색을 제거했다.
- 균일한 무채색 반투명 표면, 0.6dp의 얇은 테두리, 흰색 조작부로 바꿨다.
- 뒤쪽 배경과 앞서 그려진 요소의 GraphicsLayer를 재사용해 슬라이더 안쪽에 14dp 블러를 적용했다. 슬라이더 앞에 배치한 요소와 기존 앞뒤 순서를 유지한다.
- 블러는 슬라이더 주변의 작은 영역만 그려 캡슐 모양으로 잘라 표시한다. 밝기 요소가 없는 화면은 이 배경 기록 경로를 사용하지 않는다.
- 조작 중 선명하게 표시하고, 손을 뗀 뒤 1.4초 대기 후 0.3초 동안 흐려지는 동작을 유지했다. 밝기 범위·저장·프리셋 순환·제스처 처리는 바꾸지 않았다.

단색 배경에서는 별도 색상 변화가 생기지 않는다. 사진과 요소가 비칠 때 생기는 색은 실제 뒤쪽 내용이다.

## 검증

검사 앱은 사용 앱과 분리한 `dev.kwonsu.standby.validation`이다.

- Debug·Validation·계측 APK 빌드에 성공했고, Debug Lint는 오류 0개·기존 경고 14개다.
- 전체 기기 검사에 사진 블러·그라데이션 제거 픽셀 검사를 추가했다. 줄무늬 사진의 유리 바깥 대비는 유지되고, 내부 대비는 낮아지면서 원본의 평균 밝기가 보존되는지 확인한다.
- 단색 배경에서 슬라이더 상단·하단의 실제 픽셀이 동일하고 무채색인지 확인한다.
- 기존 밝기 드래그·투명도 변화·스와이프 분리·프리셋·편집·미디어 검사를 함께 실행했다.
- 최종 APK의 실제 기기 검사 **23개가 모두 통과**했다. 저장·순환 정책 코드는 바꾸지 않았으며, 단위 검사는 이번 수정에서 재실행하지 않았다.

원본: 최종 기기 검사 (`output/brightness-0.4.1/instrumentation-final.txt`, 로컬 전용), 최종 빌드 (`output/brightness-0.4.1/build-final.txt`, 로컬 전용), 수정 diff (`output/brightness-0.4.1/slider-source.diff`, 로컬 전용).

## 실제 화면

- 조작 중 (`output/brightness-0.4.1/previews/brightness-active.png`, 로컬 전용)
- 조작하지 않을 때 (`output/brightness-0.4.1/previews/brightness-idle.png`, 로컬 전용)
- 사진 안쪽만 흐려지는 검사 (`output/brightness-0.4.1/previews/photo-backdrop-blur.png`, 로컬 전용)
- 단색 배경의 균일한 표면 (`output/brightness-0.4.1/previews/solid-no-gradient.png`, 로컬 전용)

배경 기록과 재사용 구현은 [Android 공식 GraphicsLayer 문서](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers#composable-to-bitmap)를 참고했다.

APK: fold-standby-0.4.1-debug.apk (`output/fold-standby-0.4.1-debug.apk`, 로컬 전용).

SHA-256: `8499879d6e86e826f0a9bf544aa83d9cb48e5f6beb8dd4a65e2edec6b8d92314`

## 설치와 복구

사용 앱을 `0.4.1`(versionCode 6)로 업데이트했다. 설정과 GIF 파일의 SHA-256은 검사 전·설치 직전·설치 직후 동일했다. 설정 파일에 저장된 디자인·프리셋·밝기를 그대로 보존했다.

검사 앱 두 개를 제거하고, 검사 전 켜져 있던 자동 감시를 다시 켰다. `ChargeMonitorService`의 `isForeground=true`를 확인했다.

증거: 설치와 데이터 보존 (`output/brightness-0.4.1/update-preservation.json`, 로컬 전용), 감시 복구 (`output/brightness-0.4.1/monitor-restored.txt`, 로컬 전용).

장시간 블러 표시의 전력·발열은 측정하지 않았다.
