# 전자정부프레임워크 실습 변경 내역

## 1. 코드 리뷰와 수정

| 발견 사항 | 영향 | 수정 |
|---|---|---|
| 서비스가 `SampleDAO`(iBATIS)를 호출 | MyBatis Mapper를 수정해도 실제 화면 SQL에 반영되지 않음 | `@Resource(name="sampleMapper")`와 `SampleMapper`로 연결. iBATIS DAO/SQL은 참고용으로 보존하고 Spring 등록은 비활성화 |
| 목록/전체 건수에 검색 조건이 중복 | 한쪽만 수정하면 목록과 페이지 수가 달라짐 | `<sql id="sampleSearchWhere">` + `<include>`로 공유 |
| `LIKE '%' \|\| 검색어 \|\| '%'` | MySQL/MariaDB 기본 SQL 모드에서 문자열 연결로 동작하지 않음 | MyBatis `<bind>`로 `%검색어%`를 만들고 `#{...}`로 바인딩 |
| ID + Name 선택 항목에 MyBatis 조건이 없음 | 화면 옵션과 실제 쿼리 불일치 | ID/이름 별도 입력 필드와 AND 조건 추가 |
| 모든 DB에서 `LIMIT/OFFSET` 사용 | Oracle에서 SQL 오류 발생 | `databaseIdProvider`와 DB별 목록 쿼리 사용. Oracle 12c 이상은 `OFFSET ... FETCH NEXT` |
| 검색 시 이전 페이지 번호 유지 | 좁은 검색 결과가 빈 페이지로 보일 수 있음 | 검색 버튼은 1페이지로 초기화 |
| 목록 번호 계산에 `pageSize` 사용 | 페이지당 건수와 페이지 링크 수가 다르면 번호 오류 | `pageUnit`으로 계산 |
| MySQL URL에 문자 인코딩 지정 없음 | 실제 테스트에서 한글이 `?`로 저장됨 | MySQL 예시에 `useUnicode=true&characterEncoding=UTF-8` 추가 |
| VO의 반복적인 getter/setter | 필드를 추가할 때 반복 코드 필요 | `SampleVO`, `SampleDefaultVO`에 Lombok `@Getter`, `@Setter` 적용 |

Lombok은 접근자만 생성한다. 기존 `serialVersionUID`, 기본값, 상속 구조와 `toString()` 동작은 유지한다.

## 2. Mapper 공통 코드

파일: `src/main/resources/egovframework/sqlmap/example/mappers/EgovSample_Sample_SQL.xml`

- `sampleColumns`: 조회/등록 컬럼 목록
- `sampleSearchWhere`: 목록과 건수 조회가 공유하는 검색 조건
- `sampleListQuery`: 기본 목록 SQL과 정렬
- `limitOffset`: MySQL/MariaDB 페이징

여기서 공통화는 DB 저장 함수 생성이 아니라 MyBatis SQL 조각 재사용이다. `#{...}` 바인딩을 사용하므로 입력값을 SQL 본문에 직접 붙이지 않는다.

## 3. ID와 이름 개별 조건 검색

화면에서 **ID + Name (AND)** 를 선택한다.

| 입력 | 동작 |
|---|---|
| ID만 입력 | ID 부분 일치 |
| Name만 입력 | 이름 부분 일치 |
| 둘 다 입력 | ID 부분 일치 AND 이름 부분 일치 |
| 둘 다 비어 있음 | 전체 목록 |

앞뒤 공백은 제거한다. 기존 ID/Name 단일 검색도 유지한다. 상세 화면으로 이동했다 돌아올 때 검색값을 보존한다. LIKE의 `%`, `_`는 와일드카드로 동작한다.

## 4~5. DB 전환

실제 연결 설정: `src/main/resources/db/jdbc.properties`

| DB | JDBC 드라이버 | URL |
|---|---|---|
| MySQL | `com.mysql.jdbc.Driver` | `jdbc:mysql://127.0.0.1:13306/kosa_db?useUnicode=true&characterEncoding=UTF-8` |
| MariaDB | `org.mariadb.jdbc.Driver` | `jdbc:mariadb://localhost:23306/kosa_db` |
| Oracle (최종) | `oracle.jdbc.OracleDriver` | `jdbc:oracle:thin:@localhost:1521:xe` |

각 DB의 `jdbc-*.properties.example`을 `jdbc.properties`로 복사하고 실제 계정 정보를 넣은 뒤 서버를 다시 시작한다. 예제의 `CHANGE_ME`는 반드시 실제 비밀번호로 바꾼다. 최종 `jdbc.properties`에는 확인된 Oracle 계정이 설정되어 있다. 비밀번호가 있는 실제 설정 파일은 공개 저장소나 공유 자료에 포함하지 않는다.

Oracle은 SID `xe`, 사용자 `scott`로 연결한다. JDBC 메타데이터로 DB 종류를 확인해 해당 페이징 SQL을 선택하므로 Mapper에서 DB 이름을 수동 변경할 필요가 없다.

MariaDB 서버의 실제 포트는 설치 설정상 23306이었다. 제공된 계정 인증이 실패했으므로 사용자 지시에 따라 MariaDB의 드라이버·설정 예제·스키마·SQL만 준비했고 실제 MariaDB 데이터 이전/실행은 하지 않았다. 실제 이전 경로는 **MySQL → Oracle**이다.

Oracle에 SAMPLE 114건, IDS 1건을 복사했고 전체 행의 컬럼 값을 원본과 비교했다. 기존 MySQL 데이터는 유지했다. Oracle의 빈 문자열은 NULL로 처리되므로 비교 시 이 차이를 정규화한다. 스키마 파일은 `db/schema-oracle.sql`, `db/schema-mariadb.sql`이다. 현재 Oracle에는 이미 테이블과 데이터가 있으므로 스키마 파일을 다시 실행하지 않는다.

## 6. Lombok

```java
@Getter
@Setter
public class SampleVO extends SampleDefaultVO {
    private static final long serialVersionUID = 1L;
    private String id;
    private String name;
    // ...
}
```

`pom.xml`에 Lombok을 `provided`로 추가하고 Maven annotation processor를 지정했다. 기존 Eclipse에는 Lombok javaagent 설정이 있으므로 별도 설치 없이 프로젝트 새로고침과 Maven Update로 반영한다.

## 검증 방법

`src/test/java/egovframework/example/sample/MapperIntegrationTest.java`에 실제 DB용 테스트 5건을 추가했다. 테스트 데이터 변경은 트랜잭션을 롤백한다. 실행 예:

```powershell
mvn -Dtest.jdbc.properties="C:/eGovFrameDev-4.0.0-64bit/workspace/hello/src/main/resources/db/jdbc.properties" test
mvn package
```

접속 설정을 지정하지 않은 일반 빌드에서는 DB 통합 테스트가 건너뛰어진다. 이번 검증은 MySQL과 Oracle 접속 설정을 각각 명시하여 실행한다.

## 참고 문서

- [MyBatis 동적 SQL: bind와 DB별 SQL](https://mybatis.org/mybatis-3/dynamic-sql.html)
- [Lombok Getter/Setter](https://projectlombok.org/features/GetterSetter)
- [Lombok Maven 설정](https://projectlombok.org/setup/maven)
- [MariaDB Connector/J](https://mariadb.com/docs/connectors/mariadb-connector-j/about-mariadb-connector-j)
- [Oracle SELECT와 row limiting clause](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/SELECT.html)
- [Oracle JDBC Maven 배포](https://www.oracle.com/database/technologies/maven-central-guide.html)
