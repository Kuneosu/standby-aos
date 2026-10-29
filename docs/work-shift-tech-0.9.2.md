# 참고 이미지 기반 출퇴근 게이지 — 0.9.2

2026-10-27 / versionCode 17.

사용자가 제공한 이미지의 청록색 분할 막대, 모서리를 잘라 낸 프레임, 퍼센트 배지, 프레임 위아래 장식을 Compose Canvas로 구현했다. 이미지를 늘여 표시하지 않고 실제 진행률에 맞춰 그린다.

- 가로는 퍼센트 배지를 왼쪽에 두고 막대를 오른쪽으로 채운다. 세로는 배지를 위에 두고 아래에서 위로 채운다.
- 진행된 칸은 밝은 청록색, 남은 칸은 어두운 청록색으로 구분한다. 마지막 칸도 실제 진행률만큼 부분적으로 채운다.
- 프레임은 얇은 선과 약한 광택으로 표현하며, 장식을 포함한 두께는 최대 24dp·본체는 최대 18dp다. 칸 수는 표시 길이에 맞춰 조정한다.
- 퇴근 시각은 막대 아래 작은 글자로 표시하고, 출근·퇴근 버튼에도 각진 모서리와 청록색을 적용한다. 야간 모드는 붉은 계열을 사용한다.
- 기존 위치·크기·방향·출근 기록·17시 최소 종료·9시간 계산·폭죽·12시간 대기 로직은 변경하지 않았다.

## 검증

- Debug·Validation·계측 APK 빌드 통과.
- 단위 검사 **87개 통과**, Debug·Validation Lint 각각 오류 0개·기존 경고 14개.
- Galaxy Z Fold6(Android 16)의 별도 검사 앱에서 관련 **5개 검사 통과**(7.149초).
- 가로·세로 두께 제한과 밝은 칸이 채워지는 방향을 실제 픽셀 영역의 평균 색상으로 확인했다. 분할 칸 사이 빈틈 때문에 단일 픽셀만 비교하지 않는다.
- 참고 이미지와 동일한 **58%** 상태를 캡처하여 배지·분할 칸·프레임 장식을 확인했다. 세로·완료·출근·퇴근 버튼 화면도 확인했다.
- 다이얼 선택·취소·회전 화면 터치, 100% 폭죽·퇴근, 12시간 숨김 경계, 편집기 방향·고정·저장을 재확인했다. 긴 시간 경계는 검사 시각을 주입했으며 실제 장시간 대기를 반복한 결과는 아니다.

## 설치와 보존

**2026-10-27 사용 앱 0.9.2 / versionCode 17 설치 완료.** 설치 후 자동 감시를 다시 켜고 foreground 서비스 실행과 알림 수신 연결을 확인했다.

기존 설정·근무 기록·사진·GIF **7개 파일**의 검사 전후 기기 내부 SHA-256이 동일하다. 사용자 파일 내용을 읽거나 복사하지 않았다. 별도 검사 앱 2개는 정리했다. 설치 직전·직후와 자동 감시 복구 후에도 설정·근무 기록·미디어 7개 파일의 해시가 동일함을 확인했다.

## 산출물

- 제공된 참고 이미지 (`output/work-shift-tech-0.9.2/reference.png`, 로컬 전용)
- 실제 58% 게이지 (`output/work-shift-tech-0.9.2/previews/reference-58-horizontal.png`, 로컬 전용)
- 세로 게이지 (`output/work-shift-tech-0.9.2/previews/working-vertical.png`, 로컬 전용)
- 완료·퇴근 버튼 (`output/work-shift-tech-0.9.2/previews/complete-fireworks.png`, 로컬 전용)
- 0.9.2 APK (`output/fold-standby-0.9.2-debug.apk`, 로컬 전용)
- 빌드·검사·Lint (`output/work-shift-tech-0.9.2/build-validation.txt`, 로컬 전용)
- 실기기 검사 (`output/work-shift-tech-0.9.2/instrumentation.txt`, 로컬 전용)
- 검증 집계 (`output/work-shift-tech-0.9.2/verification.json`, 로컬 전용)
- 설치·보존 상태 (`output/work-shift-tech-0.9.2/preservation.json`, 로컬 전용)
- 소스 변경 (`output/work-shift-tech-0.9.2/source.diff`, 로컬 전용)
