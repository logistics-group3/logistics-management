# 코드 품질 감사 재현 자료

[감사 보고서](../../2026-10-20-code-quality-audit.md)의 1·2·3·5번을 입증한 코드와 정제 결과다.
`5036505`와 당시 작업 트리에서 관련 단언이 실패한 기록이다. 최신 dev에서 같은 결함이 남아 있는지는 이 문서 정리에서 재실행하지 않았으며, 기본 테스트의 성공과 별도로 해석한다.

| 파일 | 용도 |
|---|---|
| [QualityAuditConcurrencyTest.java.txt](QualityAuditConcurrencyTest.java.txt) | 실제 PostgreSQL·회사 서비스로 삭제 복원과 관리자 덮어쓰기를 재현한다. 동률 시각 페이징 대조군도 포함한다. |
| [QualityAuditDeliveryStateTest.java.txt](QualityAuditDeliveryStateTest.java.txt) | 같은 모순 응답에 대한 두 배송 검증 함수의 차이를 확인한다. 실제 원격 응답 관측이 아닌 목 기반 테스트다. |
| [body-read.cjs](body-read.cjs) | 현재 api.ts를 TypeScript로 변환해 실행한다. 표준 Response의 실패 스트림으로 본문 수신 오류를 확인한다. |
| [results.json](results.json) | 기준 커밋·소스 SHA-256·테스트별 실제 결과·기본 빌드 결과다. |

Java 파일은 문서 증거이므로 `.java.txt`로 보관한다. 기본 Gradle 소스 경로에 포함되지 않는다.
새 의존성은 필요하지 않다. 저장소의 JDK toolchain·테스트 의존성·Docker와 설치된 프런트 패키지를 사용한다.

## Java 재현

저장소 루트에서 실행한다. 임시 파일·JUnit XML·HTML은 `$audit_tmp` 아래에 생성한다.
명령은 기존 서비스 스택을 기동하거나 운영 DB에 접속하지 않는다. PostgreSQL Testcontainers가 새 테스트 DB를 만든다.

```bash
audit_tmp=$(mktemp -d)
audit_evidence="$PWD/docs/reviews/code-quality/2026-10-20"
mkdir -p "$audit_tmp/company" "$audit_tmp/order"
cp "$audit_evidence/QualityAuditConcurrencyTest.java.txt" "$audit_tmp/company/QualityAuditConcurrencyTest.java"
cp "$audit_evidence/QualityAuditDeliveryStateTest.java.txt" "$audit_tmp/order/QualityAuditDeliveryStateTest.java"
cat > "$audit_tmp/repro.init.gradle" <<EOF
gradle.projectsEvaluated {
    gradle.rootProject.project(':company-service') {
        sourceSets.test.java.srcDir('$audit_tmp/company')
        tasks.named('test') {
            reports.junitXml.outputLocation = file('$audit_tmp/company-xml')
            reports.html.outputLocation = file('$audit_tmp/company-html')
            testLogging.showStandardStreams = true
        }
    }
    gradle.rootProject.project(':order-service') {
        sourceSets.test.java.srcDir('$audit_tmp/order')
        tasks.named('test') {
            reports.junitXml.outputLocation = file('$audit_tmp/order-xml')
            reports.html.outputLocation = file('$audit_tmp/order-html')
        }
    }
}
EOF
./gradlew -I "$audit_tmp/repro.init.gradle" :company-service:test --tests '*QualityAuditConcurrencyTest' :order-service:test --tests '*QualityAuditDeliveryStateTest' --continue
```

감사 당시에는 company 3개 중 2개 실패, order 1개 실패였다. company 대조군인 120개 업체의 동률 시각 페이징은 통과했다.
관리자 이전의 경쟁 트랜잭션은 실제 Entity·Repository의 잠금 조회와 `assignManager`를 사용했다. 원격 사용자 존재 검사는 이 재현 범위에 포함하지 않았다.
이 코드는 감사 시점의 동시 실행 순서를 강제하는 입증 도구다. 잠금 도입 후 회귀 테스트로 채택할 때는 기존 `CompanyProductConcurrencyIntegrationTest`처럼 경쟁 작업의 잠금 대기를 관측하고 대기를 해제하도록 바꾼다.

## 프런트 본문 오류 재현

```bash
node docs/reviews/code-quality/2026-10-20/body-read.cjs "$PWD"
```

감사 당시 출력은 다음과 같았고, 기대한 ApiError 변환이 없어 exit 1이었다.

```text
AUDIT_BODY_ERROR_TYPE=TypeError
AUDIT_CREATE_FORM_MARKS_UNCERTAIN=false
```

실제 `api.ts` 함수를 실행하되 fetch 결과를 주입한다. 폼의 `instanceof ApiError` 조건을 같은 방식으로 평가하며 실제 React 폼을 렌더링하지 않는다.
실제 서버 저장·브라우저 재등록까지 수행한 E2E 증거는 아니다.

## 기본 검증으로 돌아가기

```bash
./gradlew build
```

감사에서는 이 명령으로 기본 company 50개·order 484개를 다시 실행해 성공을 확인했다. 전체 XML 857개에는 나머지 모듈의 재사용 결과가 포함된다.
재현용 Java 파일은 처음부터 임시 경로에만 만들었으므로 서비스·기존 테스트 소스에 되돌릴 변경은 없다.
`body-read.cjs`의 TypeScript 변환은 메모리 안에서 수행한다.
