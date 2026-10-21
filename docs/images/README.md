# 이미지

| 파일 | 내용 |
|---|---|
| `architecture.svg` | README의 서비스 아키텍처. 아래 아이콘의 경로를 SVG 안에 직접 넣어 한 파일로 열린다 |
| `erd-user-company.svg`, `erd-hub.svg`, `erd-order.svg`, `erd-delivery.svg` | README의 서비스별 ERD. 모든 컬럼과 타입, PK·FK·UK, 서비스 사이 논리 참조를 보여 준다 |
| `icons/*.svg` | 아키텍처 이미지에 쓴 기술 로고 15개 |

## 아이콘 출처
- [simple-icons](https://github.com/simple-icons/simple-icons) 14.15.0의 SVG를 그대로 받았다. 라이선스는 CC0-1.0이다.
- 로고와 상표는 각 소유자의 것이며, 이 저장소에서는 사용한 기술을 표시하는 용도로만 쓴다.
- simple-icons에 없는 Zipkin, OpenFeign, Caffeine은 아이콘 없이 글자로 표기했다.

## 다시 만들 때
`architecture.svg`는 요소마다 `<g transform>`과 `<text>`로 놓여 있어 편집기에서 직접 고치면 된다.

ERD 이미지는 손으로 고치지 않고 스키마에서 다시 뽑는다. 임시 PostgreSQL 16에 서비스별 마이그레이션을 `V` 번호 순서로 적용한 뒤(`search_path`를 서비스 스키마로 지정), `information_schema.columns`와 `pg_constraint`, 유니크 인덱스를 읽어 SVG로 그렸다. 마이그레이션을 추가했으면 같은 방식으로 다시 만들어야 하며, 서비스 사이 논리 참조(→ 표시)는 FK가 없어 스키마에서 알 수 없으므로 직접 관리한다.
