# 이벤트 설계 v0.1 — 주문·결제·정산

> 이벤트 스토밍 결과 정리. Phase 0 산출물.
> 상태: Draft — Phase 2(이벤트 드리븐 전환) 진입 시 갱신 예정.

## 1. 범위와 핵심 결정

- **Saga 보상 범위**: 재고 차감 포함. 재고는 별도 서비스가 아니라 **주문 서비스 내부 애그리거트**로 시작한다. (서비스 수 최소화, 동시성 제어 시나리오 확보)
- **환불 흐름**: 포함. 결제 승인 후 고객 취소 → 환불 → 정산 차감까지 다룬다.
- **취소는 2단계로 처리**: `OrderCancelRequested` → 환불 완료(`PaymentCancelled`) 확인 후 → `OrderCancelled` 확정. 환불 실패 시 주문만 취소되는 정합성 깨짐을 방지한다. *(ADR 후보 #4)*
- 미포함(Out of scope): 회원/인증(토큰 검증 모킹), 상품 관리(시드 데이터), 실제 PG 연동(모의 PG 서버), 배송.

## 2. 흐름 정의

### 2.1 해피 패스

1. `[커맨드]` 주문 생성 (고객 → 주문 서비스, 멱등키 포함)
2. 주문 서비스: 재고 선점 + 주문 저장 (단일 로컬 트랜잭션, 동시성 제어 지점)
3. `[이벤트]` **OrderCreated** 발행
4. 결제 서비스 구독 → 모의 PG 승인 요청
5. `[이벤트]` **PaymentApproved** 발행
6. 주문 서비스 구독 → 주문 확정 → `[이벤트]` **OrderConfirmed** 발행
7. 정산 서비스가 PaymentApproved 구독 → 정산 대상 등록 (일배치 입력)

### 2.2 결제 실패 (Saga 보상)

1. 결제 서비스: PG 거절 → `[이벤트]` **PaymentFailed** 발행 (사유 포함)
2. 주문 서비스 구독 → 보상 실행: 재고 복원 + 주문 취소 (단일 로컬 트랜잭션)
3. `[이벤트]` **OrderCancelled** 발행 (reason = PAYMENT_FAILED)

### 2.3 결제 타임아웃

- 타임아웃은 실패가 아니다 — PG가 이미 승인했을 수 있다.
- 처리: 재시도 N회(지수 백오프) → 그래도 불명확하면 **승인 상태 조회 API**로 확인 → 최종 판정 후 PaymentApproved 또는 PaymentFailed 발행.
- 모의 PG에 지연/무응답 모드를 만들어 이 시나리오를 재현 가능하게 한다. *(Phase 5 장애 실험 재료)*

### 2.4 고객 취소 (환불)

1. `[커맨드]` 주문 취소 (고객 → 주문 서비스, 확정 상태에서만 허용)
2. 주문 서비스: 상태를 `CANCEL_REQUESTED`로 전이 → `[이벤트]` **OrderCancelRequested** 발행
3. 결제 서비스 구독 → 모의 PG 환불 요청 → `[이벤트]` **PaymentCancelled** 발행
4. 주문 서비스 구독 → 재고 복원 + 주문 취소 확정 → `[이벤트]` **OrderCancelled** 발행 (reason = CUSTOMER_REQUEST)
5. 정산 서비스가 PaymentCancelled 구독 → **차감(마이너스) 정산 레코드** 등록

## 3. 이벤트 카탈로그

| 이벤트 | 발행자 | 구독자 | 트리거 | 비고 |
|---|---|---|---|---|
| OrderCreated | 주문 | 결제 | 주문 생성 + 재고 선점 성공 | Saga 시작점 |
| OrderConfirmed | 주문 | (현재 없음) | PaymentApproved 수신 | 알림 등 확장 대비 |
| OrderCancelRequested | 주문 | 결제 | 고객 취소 커맨드 | 환불 트리거 |
| OrderCancelled | 주문 | (현재 없음) | 보상 완료 or 환불 확정 | reason 필드 필수 |
| PaymentApproved | 결제 | 주문, 정산 | PG 승인 성공 | 정산 등록 트리거 |
| PaymentFailed | 결제 | 주문 | PG 거절 / 최종 실패 판정 | 보상 트리거 |
| PaymentCancelled | 결제 | 주문, 정산 | PG 환불 성공 | 정산 차감 트리거 |

## 4. 토픽 설계

| 토픽 | 담는 이벤트 | 파티션 키 | 비고 |
|---|---|---|---|
| `order.events` | OrderCreated, OrderConfirmed, OrderCancelRequested, OrderCancelled | orderId | 같은 주문의 이벤트 순서 보장 |
| `payment.events` | PaymentApproved, PaymentFailed, PaymentCancelled | orderId | paymentId가 아닌 orderId — 주문 단위 순서 보장 목적 |
| `order.events.dlq` | 처리 실패 메시지 | - | 재시도 소진 후 격리 |
| `payment.events.dlq` | 처리 실패 메시지 | - | 재시도 소진 후 격리 |

- 파티션 수: 로컬 3 (컨슈머 스케일링 실험용). 운영 가정 수치는 부하테스트 후 결정.
- 토픽당 다중 이벤트 타입 vs 이벤트별 토픽: **v0.1은 서비스당 단일 토픽** 채택. *(ADR 후보 #5 — 트레이드오프 기록)*

## 5. 이벤트 봉투(Envelope) 공통 스키마

```json
{
  "eventId": "uuid — 컨슈머 멱등 처리 키",
  "eventType": "OrderCreated",
  "schemaVersion": 1,
  "occurredAt": "2026-06-10T12:00:00+09:00",
  "correlationId": "uuid — 주문 단위 추적, 트레이스 연결",
  "payload": { }
}
```

- 컨슈머는 `eventId` 기준으로 처리 이력을 저장해 중복 수신을 무시한다(멱등 컨슈머).
- `correlationId`는 OTel 트레이스와 연결해 "주문 하나의 전 여정"을 추적하는 데 쓴다.

## 6. 페이로드 초안 (대표 2건)

### OrderCreated

```json
{
  "orderId": "uuid",
  "customerId": "uuid",
  "lines": [
    { "productId": "uuid", "quantity": 2, "unitPrice": 15000 }
  ],
  "totalAmount": 30000,
  "currency": "KRW"
}
```

### PaymentApproved

```json
{
  "paymentId": "uuid",
  "orderId": "uuid",
  "amount": 30000,
  "currency": "KRW",
  "pgTransactionId": "string — 대사(reconciliation) 매칭 키",
  "approvedAt": "2026-06-10T12:00:03+09:00"
}
```

- 방침: zero-payload(ID만)와 full-payload 사이에서 **구독자가 추가 조회 없이 처리 가능한 최소 필드**를 담는다. *(ADR 후보 #6)*
- 금액은 정수(원 단위) 저장. 소수점 통화 확장 시 BigDecimal + 통화별 스케일 정책 문서화.

## 7. 미해결 질문 → ADR 후보

| # | 질문 | 메모 |
|---|---|---|
| 5 | 서비스당 단일 토픽 vs 이벤트별 토픽 | 순서 보장, 컨슈머 구성, 스키마 진화 |
| 6 | 페이로드 크기 정책 | zero vs full, 개인정보 포함 여부 |
| 7 | Saga 방식: 코레오그래피(현재 초안) vs 오케스트레이터 도입 | Phase 3 진입 전 결정 |
| 8 | 재고 동시성 제어 1차 구현: 비관적 락 vs 낙관적 락 vs Redis | Phase 1에서 실험 후 수치로 결정 |

> #4(취소 2단계 처리)는 **ADR-0003으로 작성 완료**되어 목록에서 제외함.

---

*변경 이력: v0.1 — 이벤트 스토밍 초안 (Phase 0) · v0.1.1 — ADR-0003 반영*
