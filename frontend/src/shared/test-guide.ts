/**
 * 시나리오 테스트 가이드에 보여 줄 실행 주소·계정·시나리오.
 * 계정과 데이터는 script/ui-test-seed.py가 실제 API로 만든다. 주소는 script/ui-test.sh의 기본 포트다.
 * 시나리오의 기대 결과는 실제 서버 동작(2026-10-16 확인)을 적는다. 화면에서 새 계약을 정의하지 않는다.
 */
export const TEST_PASSWORD = 'UiLocal!2026'

export interface TestLink {
  label: string
  url: string
  description: string
}

export const testLinks: TestLink[] = [
  {
    label: 'Swagger',
    url: 'http://127.0.0.1:18080/swagger-ui.html',
    description: 'API 명세와 직접 호출. 오른쪽 위 서비스 선택에서 문서를 바꾼다',
  },
  {
    label: 'Grafana',
    url: 'http://127.0.0.1:13000',
    description: '요청 수·오류·응답 시간 대시보드. 로그인 없이 볼 수 있다',
  },
  {
    label: 'Zipkin',
    url: 'http://127.0.0.1:19411/zipkin/?serviceName=gateway-service',
    description: '요청 하나가 거친 서비스별 구간(분산 추적)',
  },
  {
    label: 'Prometheus',
    url: 'http://127.0.0.1:19090/targets',
    description: 'Eureka로 찾은 메트릭 수집 대상 6개',
  },
  {
    label: 'Eureka',
    url: 'http://127.0.0.1:18761',
    description: '등록된 서비스 인스턴스',
  },
]

export type AccountName = 'uimaster' | 'uiuser' | 'uiviewer' | 'uidriver' | 'uidrvgg' | 'uidrvbs'

export interface TestAccount {
  username: AccountName
  name: string
  role: string
  checks: string
}

export const testAccounts: TestAccount[] = [
  {
    username: 'uimaster',
    name: '마스터',
    role: 'MASTER',
    checks: '모든 기능. 업체 담당자 변경, 배송담당자 지정, 주문 삭제',
  },
  {
    username: 'uiuser',
    name: '김서연',
    role: 'USER · 한빛 리테일 담당자',
    checks: '본인 업체로 주문 생성·조회·취소, 본인 업체·상품 수정',
  },
  {
    username: 'uiviewer',
    name: '박지민',
    role: 'USER · 새봄 유통 담당자',
    checks: '다른 업체·다른 사람 주문이 막히는지',
  },
  {
    username: 'uidriver',
    name: '이준호',
    role: 'USER · 서울특별시 센터 배송담당자',
    checks: '배정된 배송 구간의 출발·도착 처리',
  },
  {
    username: 'uidrvgg',
    name: '최현우',
    role: 'USER · 경기 북부 센터 배송담당자',
    checks: '경기 북부에서 출발하는 집하·허브 간 이동 처리, 서울 배송은 볼 수 없는지(403)',
  },
  {
    username: 'uidrvbs',
    name: '정다은',
    role: 'USER · 부산광역시 센터 배송담당자',
    checks: '부산에 도착한 배송의 수령업체 배송 구간 처리',
  },
]

export type ScenarioArea =
  '인증·Gateway' | '허브' | '업체·상품' | '주문·배송' | '사용자' | '공통·관측'

export interface Scenario {
  id: string
  area: ScenarioArea
  title: string
  /** 발제문 필수 기능 가이드의 해당 항목 */
  requirement: string
  account?: AccountName
  /** 로그인 후 바로 열 화면 */
  path?: string
  /** 계정 버튼 대신 보여 줄 진행 위치 안내 */
  hint?: string
  steps: string[]
  expected: string
  /** 서버 기능이 아직 없어 기대 결과가 발제와 다른 시나리오 */
  limited?: boolean
}

export const scenarios: Scenario[] = [
  {
    id: 'login',
    area: '인증·Gateway',
    title: '로그인하면 JWT가 발급되고, 새로고침하면 다시 로그인한다',
    requirement: '로그인 성공 시 JWT 발급, 자체 JWT 인증',
    account: 'uiuser',
    path: '/',
    steps: [
      '로그인 화면에서 uiuser 계정으로 로그인한다.',
      '대시보드 오른쪽 위에 이름과 소속이 보이는지 확인한다.',
      '브라우저를 새로고침한다.',
    ],
    expected:
      '로그인 후 대시보드가 열린다. 토큰은 메모리에만 있으므로 새로고침하면 로그인 화면으로 돌아간다.',
  },
  {
    id: 'gateway-auth',
    area: '인증·Gateway',
    title: '토큰 없는 요청과 위조한 권한 헤더는 Gateway가 막는다',
    requirement: 'API Gateway 인증·권한 확인, 요청마다 저장된 권한과 비교',
    steps: [
      'Swagger에서 hub-service 문서를 고르고 Authorize 없이 GET /api/v1/hubs를 실행한다.',
      'auth 문서의 POST /api/v1/auth/login으로 uiuser 토큰을 받아 Authorize에 넣고(Bearer 없이) 다시 실행한다.',
      'user-service 문서의 GET /api/v1/users(관리자 전용)를 uiuser 토큰으로 실행한다.',
    ],
    expected:
      '토큰이 없으면 401 UNAUTHORIZED, 토큰을 넣으면 200, USER가 관리자 API를 부르면 403 FORBIDDEN이다. /internal/** 경로는 외부에서 404다.',
  },
  {
    id: 'signup',
    area: '인증·Gateway',
    title: '회원가입은 승인 없이 바로 활성화된다',
    requirement: '회원가입 즉시 활성화, username·password 규칙',
    hint: '로그아웃한 뒤 로그인 화면의 회원가입에서 진행합니다.',
    steps: [
      '로그아웃한 상태에서 로그인 화면의 회원가입으로 이동한다.',
      '아이디에 대문자나 3자 이하를 넣어 안내를 확인한다(소문자·숫자 4~10자).',
      '비밀번호에 특수문자를 빼고 넣어 안내를 확인한다(8~15자, 대소문자·숫자·특수문자).',
      '규칙에 맞게 가입한 뒤 바로 로그인한다.',
    ],
    expected:
      '가입 직후 승인 절차 없이 로그인된다. 새 회원은 USER이며 배송담당자 지정은 관리자가 한다. Slack은 멤버 ID 대신 가입 이메일을 받는다(ADR 0016).',
  },
  {
    id: 'hubs',
    area: '허브',
    title: '허브 17개를 조회·검색한다',
    requirement: '허브 초기 데이터 적재, 조회·검색은 모든 로그인 사용자',
    account: 'uiviewer',
    path: '/hubs',
    steps: ['허브 관리 화면을 연다.', '검색어에 "부산"을 넣는다.'],
    expected:
      '17개 센터와 주소가 보이고 검색어로 좁혀진다. 허브는 고정 데이터라 생성·수정·삭제 버튼이 없다.',
  },
  {
    id: 'hub-routes',
    area: '허브',
    title: '허브 간 이동정보(P2P 경로)를 조회·검색한다',
    requirement: '허브 간 이동경로 엔티티, 초기 데이터, 조회·검색은 모든 로그인 사용자',
    steps: [
      'Swagger hub-service 문서에서 uiuser 토큰으로 GET /api/v1/hub-routes를 실행한다.',
      'GET /api/v1/hubs로 경기 북부·부산 센터의 hubId를 찾아 departureHubId, arrivalHubId에 넣는다.',
      'sort에 distanceKm,desc를 넣어 다시 실행한다.',
    ],
    expected:
      '전체 272개(17개 허브의 모든 방향 쌍)가 나온다. 경기 북부 → 부산은 1건이고 거리·소요시간(약 443km·380분)이 있다. 정렬이 바뀐다. 경로는 고정 데이터라 생성·수정 API가 없다.',
  },
  {
    id: 'company-owner',
    area: '업체·상품',
    title: '업체 수정은 담당자 본인 또는 관리자만 할 수 있다',
    requirement: '업체 CRUD + Search, 생성·수정은 업체 담당자 본인 또는 관리자',
    account: 'uiuser',
    path: '/companies',
    steps: [
      '업체 관리에서 "한빛 리테일" 상세를 열어 업체 정보 수정이 되는지 확인한다.',
      '"스파르타 서플라이" 상세를 연다.',
      'uimaster로 다시 로그인해 같은 업체의 "업체 정보·담당자 변경"을 확인한다.',
    ],
    expected:
      '본인 업체는 수정할 수 있고, 다른 업체는 "다른 업체 담당자"로 표시되며 수정 버튼이 없다. 관리자는 모든 업체의 정보와 담당자를 바꾼다. 조회·검색은 모두 가능하다.',
  },
  {
    id: 'company-hub-check',
    area: '업체·상품',
    title: '업체를 만들 때 관리 허브가 실제로 있는지 확인한다',
    requirement: '업체 추가·수정 시 관리 허브 ID 존재 확인',
    steps: [
      'Swagger company-service 문서에서 uiuser 토큰으로 POST /api/v1/companies를 실행한다.',
      'hubId에 없는 UUID(예: 11111111-1111-1111-1111-111111111111)를 넣는다.',
    ],
    expected:
      '404 HUB_NOT_FOUND이고 업체가 만들어지지 않는다. company-service가 hub-service 내부 API(I-02)로 확인한다.',
  },
  {
    id: 'product',
    area: '업체·상품',
    title: '상품은 담당 업체 아래에만 등록한다',
    requirement: '상품은 업체 서비스의 하위 리소스, 업체 담당자 또는 관리자만 등록',
    account: 'uiuser',
    path: '/products',
    steps: [
      '상품 관리에서 상품 등록을 연다.',
      '담당 업체 선택에 본인 업체(한빛 리테일)만 나오는지 확인하고 상품을 등록한다.',
    ],
    expected:
      '본인 업체 상품으로 등록된다. 다른 업체에는 등록할 수 없다(서버도 403으로 막는다). 상품 검색은 모든 사용자가 한다.',
  },
  {
    id: 'order-create',
    area: '주문·배송',
    title: '주문하면 배송과 배송 경로 기록이 함께 생긴다',
    requirement: '주문 생성 시 배송·배송 경로 기록 생성, Slack Webhook 호출',
    account: 'uiuser',
    path: '/orders/new',
    steps: [
      '새 주문 등록에서 공급업체 "스파르타 서플라이", 수령업체 "한빛 리테일"을 고른다.',
      '상품과 수량을 넣고 주문 등록을 누른다.',
      '주문 상세의 배송으로 이동해 구간을 확인한다.',
    ],
    expected:
      '주문이 "주문 완료"로 생기고 배송이 함께 만들어진다. 같은 허브라 공급업체 집하·수령업체 배송 2구간이며, 각 구간에 서울 허브 배송담당자가 자동 배정된다. Slack 알림은 테스트 서버에서 꺼져 있다.',
  },
  {
    id: 'order-scope',
    area: '주문·배송',
    title: '주문자는 자기 주문만 본다',
    requirement: '주문 조회·검색: 주문자 본인은 자신의 주문만',
    account: 'uiviewer',
    path: '/orders',
    steps: [
      '주문 관리 목록을 본다(uiuser가 만든 주문이 있는지).',
      'uiuser 주문 상세 주소(/orders/주문ID)를 직접 연다.',
    ],
    expected:
      '목록에는 본인 주문만 나오고, 다른 사람 주문 상세는 권한 오류(403)다. 관리자는 전체 주문을 본다.',
  },
  {
    id: 'order-cancel',
    area: '주문·배송',
    title: '주문을 취소하면 배송도 취소된다',
    requirement: '주문 취소, 여러 서비스 데이터 일관성',
    account: 'uiuser',
    path: '/orders',
    steps: [
      '상태가 "주문 완료"(집하 대기)인 본인 주문 상세를 연다.',
      '주문 취소를 누르고 확인한다.',
    ],
    expected:
      '주문이 "취소"가 되고 연결된 배송도 "취소"다. 집하가 시작된 주문은 409 INVALID_STATUS_TRANSITION으로 취소할 수 없다.',
  },
  {
    id: 'order-delete',
    area: '주문·배송',
    title: '주문 삭제는 관리자만, 물리 삭제가 아니라 논리 삭제다',
    requirement: 'DELETE는 deleted_at·deleted_by 논리 삭제, 조회·검색에서 제외',
    account: 'uimaster',
    path: '/orders',
    steps: [
      '취소된 주문 상세에서 주문 삭제 → 삭제 확인을 누른다.',
      'Swagger에서 그 주문 ID로 GET /api/v1/orders/{orderId}를 실행한다.',
      '(선택) docker compose -p logistics-ui-test exec postgres psql -U postgres -d logistics -c "select order_id, deleted_at, deleted_by from order_schema.p_orders where deleted_at is not null"',
    ],
    expected:
      '목록과 조회에서 사라지고 404 ORDER_NOT_FOUND다. 연결된 배송·배송 구간도 함께 논리 삭제된다. DB 행은 남고 deleted_at·deleted_by가 채워진다.',
  },
  {
    id: 'delivery-routes',
    area: '주문·배송',
    title: '배송 구간은 담당자 본인 또는 관리자만 처리한다',
    requirement: '배송 수정은 담당자 본인 또는 관리자, 담당자는 본인 배송만 조회',
    account: 'uidriver',
    path: '/deliveries',
    steps: [
      '배송 관리에서 배정된 배송 상세를 연다.',
      '공급업체 집하 출발 처리 → 도착 처리 → 수령업체 배송 출발 → 도착 순으로 누른다.',
      'uidrvgg(경기 북부 담당자)로 로그인해 같은 배송 주소를 연다.',
      'uiviewer(일반 사용자)로 같은 배송을 연다.',
    ],
    expected:
      '담당자는 순서대로 처리해 "배송 완료"가 된다. 다른 허브 담당자는 403이다. 일반 사용자는 조회만 하고 처리 버튼이 없다.',
  },
  {
    id: 'rollback',
    area: '주문·배송',
    title: '배송을 만들 수 없으면 주문도 남지 않는다(실패 시 롤백)',
    requirement: '실패 시 롤백 처리 시나리오 1개 이상',
    account: 'uimaster',
    path: '/orders/new',
    steps: [
      '새 주문 등록에서 공급업체 "대전 사무용품 공급", 수령업체 "대전 오피스 리테일", 상품 "서류 보관함"을 고른다.',
      '주문 등록을 누른다.',
      '주문 관리 목록과 Grafana의 "주문 생성 응답"을 확인한다.',
    ],
    expected:
      '대전 허브에는 배송담당자가 없어 배송 생성이 실패하고, 주문 등록은 202로 접수된 뒤 진행 조회에서 ABORTED(DELIVERY_MANAGER_NOT_FOUND)로 끝나고 화면에 실패 사유가 보인다. 주문 목록에 새 주문이 없다. 주문 서비스가 Saga로 주문 기록을 ABORTED로 정리한다.',
  },
  {
    id: 'cross-hub',
    area: '주문·배송',
    title: '발제 예시: 경기 업체 상품을 부산 업체로 배송한다',
    requirement: '허브 간 이동 후 업체 배송(1단계 킥오프 예시), 담당자는 본인 구간만 처리',
    account: 'uimaster',
    path: '/orders/new',
    steps: [
      '새 주문 등록에서 공급업체 "경기 건조식품 가공", 수령업체 "부산 수산 도매", 상품 "마른오징어 가공품" 50개를 고르고 주문 등록을 누른다.',
      '주문 상세의 배송 상세에서 구간 3개와 담당자를 확인한다.',
      '가이드에서 uidrvgg로 바꿔 같은 배송의 집하·허브 간 이동을 출발 → 도착 순으로 처리한다. 허브 간 이동 도착 때 실제 거리·소요시간을 입력한다.',
      'uidrvbs로 바꿔 수령업체 배송을 출발 → 도착 처리한다.',
    ],
    expected:
      '배송은 공급업체 집하(경기 북부 담당자) → 허브 간 이동(경기 북부 → 부산, 예상 약 443km·380분, 경기 북부 담당자) → 수령업체 배송(부산 담당자) 3구간이다. 각 담당자는 자기 구간만 처리 버튼이 보이고, 앞 구간이 도착해야 다음 구간이 출발한다. 허브 간 이동에는 예상값과 입력한 실제 거리·시간이 함께 남는다. 마지막에 "배송 완료"가 된다.',
  },
  {
    id: 'users-scope',
    area: '사용자',
    title: '사용자 조회는 본인만, 관리자는 전체를 본다',
    requirement: '사용자 생성·수정·삭제는 관리자, 조회·검색은 본인(관리자는 전체)',
    account: 'uiuser',
    path: '/users',
    steps: ['사용자 관리 화면을 연다.', 'uimaster로 다시 로그인해 같은 화면을 연다.'],
    expected:
      'USER는 내 프로필만 보고 "전체 사용자 목록은 관리자에게 제공됩니다"가 나온다. 관리자는 전체 사용자를 검색한다.',
  },
  {
    id: 'delivery-manager',
    area: '사용자',
    title: '배송담당자는 별도 엔티티가 아니라 사용자 권한으로 지정한다',
    requirement: '배송담당자는 가입한 유저에게 권한으로 부여',
    account: 'uimaster',
    path: '/users',
    steps: [
      '사용자 관리에서 방금 가입한 사용자의 상세를 연다.',
      '배송 담당자로 지정을 체크하고 허브를 골라 저장한다.',
    ],
    expected:
      '그 사용자가 배송담당자가 되고 소속 허브가 바뀐다. 이후 그 허브에서 생기는 배송 구간의 자동 배정 후보가 된다.',
  },
  {
    id: 'search-sort',
    area: '공통·관측',
    title: '검색 조건과 정렬, 기본은 생성일 역순이다',
    requirement: '검색 조건·정렬(기본 생성일 역순)',
    account: 'uimaster',
    path: '/orders',
    steps: [
      '주문 관리에서 정렬을 "최근 주문순", "오래된 주문순", "수량 많은 순"으로 바꾼다.',
      '공급업체·상태 필터를 고른다.',
    ],
    expected:
      '처음에는 최근 주문순이고, 정렬·필터에 맞게 목록이 바뀐다. 페이지 크기는 서버가 10·30·50만 허용한다.',
  },
  {
    id: 'tracing',
    area: '공통·관측',
    title: '주문 요청 하나가 거친 서비스를 Zipkin에서 본다',
    requirement: '(선택) Zipkin 분산 추적',
    steps: [
      '주문을 하나 만든 뒤 Zipkin을 연다.',
      'serviceName=gateway-service, spanName=http post로 찾고 가장 최근 trace를 연다.',
    ],
    expected:
      'gateway → user(권한 재확인) → order → company(업체·상품 확인) → delivery → user(담당자 조회) 순서의 구간 나무가 보인다.',
  },
  {
    id: 'metrics',
    area: '공통·관측',
    title: '시나리오 결과를 Grafana 대시보드로 확인한다',
    requirement: '서비스 상태와 요청 결과 관측(발제 범위 밖 추가 기능)',
    steps: [
      'Grafana 첫 화면 "스파르타 물류 서비스 개요"를 연다.',
      '"서비스 상태", "엔드포인트별 요청·상태", "Feign 호출 수"를 본다.',
      '"서비스별 p95 응답 시간"의 점을 눌러 Zipkin trace로 이동한다.',
    ],
    expected:
      '서비스 6개가 UP이고, 앞의 시나리오에서 보낸 요청이 상태 코드별로 쌓여 있다. 수집 주기가 15초라 반영까지 30초쯤 걸린다.',
  },
]

export const knownGaps = [
  '허브 간 이동은 출발 허브에서 도착 허브로 바로 가는 P2P 1구간이다. 중계 허브를 거치는 경로 계산은 하지 않는다(발제의 P2P 기준).',
  '재고 감소·복원·부족 시 실패는 구현하지 않았다. 설계 검토 피드백으로 고도화로 옮겼다(SA v2.0).',
  '주문 생성은 수령업체 담당자 본인 또는 관리자만 한다. 다른 업체 명의 주문을 막으려는 팀 결정이다(SA 1.5 #11).',
  '회원가입은 Slack 멤버 ID 대신 Slack 가입 이메일을 받는다(ADR 0016).',
  '이 테스트 서버는 Slack Webhook·DM을 끈 상태로 실행된다.',
]
