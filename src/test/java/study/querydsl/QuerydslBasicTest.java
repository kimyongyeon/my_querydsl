package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDTO;
import study.querydsl.dto.QMemberDTO;
import study.querydsl.dto.UserDTO;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import java.util.List;

import static com.querydsl.jpa.JPAExpressions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.in;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;


@SpringBootTest
@Transactional
public class QuerydslBasicTest {
    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em); // 멀티스레드, 동시성 문제 없이 돌아가도록 스프링이 알아서 해줌.
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");

        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);

        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);

    }

    @Test
    public void startJPQL() {
        String qStr = "select m from Member m where m.username = :username";
        Member findByJPQL = em.createQuery(qStr, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findByJPQL.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl() {

        QMember m = new QMember("m"); // 셀프조인할때만 선언해서 사용하도록. 그렇지 않을때는 static으로 사용하도록.

        Member findMember = queryFactory
                .select(m) // static member로 사용 가능 하니 권장함.
                .from(m)
                .where(m.username.eq("member1")) // prepare 방식으로 바인딩 한다. 해킹 위험 요소 제거
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search() {
        Member findMember = queryFactory.selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.eq(10)))
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam() { // and만 있으면 이방식이 더 선호함.
        Member findMember = queryFactory.selectFrom(member)
                .where(member.username.eq("member1"),
                        (member.age.eq(10)), null // null 이 있으면 동적쿼리 할때 기가 막히게 짤수있다고 함.
                )
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void resultFetch() {
//        List<Member> fetch = queryFactory
//                .selectFrom(member)
//                .fetch();
//
//        Member fetchOne = queryFactory
//                .selectFrom(QMember.member)
//                .fetchOne();
//
//        Member fetchFirst = queryFactory
//                .selectFrom(QMember.member)
//                .fetchFirst();

        // 쿼리 두번 실행 한다.
//        QueryResults<Member> results = queryFactory
//                .selectFrom(member)
//                .fetchResults();
//
//        results.getTotal(); // 카운트
//        List<Member> content = results.getResults();  // 콘텐츠 가져온다.

        // 카운트만 가져온다.
        long total = queryFactory
                .selectFrom(member)
                .fetchCount();

    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순(DESC)
     * 2. 회원 이름 올림차순(ASC)
     * 단, 2에서 회원 이름이 업승면 마지막에 출력(nulls last)
     */
    @Test
    public void sort() {

        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();

    }

    @Test
    public void paging1() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    public void paging2() {
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);
        assertThat(queryResults.getResults().size()).isEqualTo(2);

    }

    @Test
    public void aggregation() {
        List<Tuple> result = queryFactory // 실무에서는 tuple을 많이 사용하지 않는다.
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                ).from(member)
                .fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구해라.
     *
     * @throws Exception
     */
    @Test
    public void group() throws Exception {
        // given
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();
        // when
        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        // then
        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15); // (10 + 20 ) / 2

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35); // (30 + 40 ) / 2
    }

    /**
     * 팀 A에 소속된 모든 회원
     *
     * @throws Exception
     */
    @Test
    public void join() throws Exception {
        List<Member> result = queryFactory
                .selectFrom(member)
                .leftJoin(member.team, team) // join, leftjoin
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    /**
     * 세타 조인
     * 회원이 이름이 팀 이름과 같은 회원 조회
     * 연관관계가 없어도 조인이 가능함. innerjoin, leftjoin은 안된다. 하지만 on절에 조건을 넣어서 가능하다. 하이버네이트 상위버전만.
     *
     * @throws Exception
     */
    @Test
    public void theta_join() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team) // 모든 회원 , 모든 팀 다 가져와서 조건건다. DB가 최적화를 알아서 한다. 카르테시안 곱
                .where(member.username.eq(team.name))
                .fetch();
        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");


    }

    /**
     * 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL: select m, t from Member m left join m.team t on t.name = 'teamA'
     *
     * @throws Exception
     */
    @Test
    public void join_on_filtering() throws Exception {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team) // inner join 이면 그냥 where 조건으로 거는게 낳다.
                .on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple: " + tuple);
        }
    }

    @Test
    public void join_on_no_relation() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name)) // 필터 , 대상을 줄인다.
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoinNo() throws Exception {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("패치 조인 미적용").isFalse();
    }

    @Test
    public void fetchJoinUse() throws Exception {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin() // 연관된 팀을 한번에 가져온다.
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("패치 조인 미적용").isTrue();
    }

    /**
     * 나이가 가장 많은 회원 조회
     *
     * @throws Exception
     */
    @Test
    public void subQuery() throws Exception {

        QMember ms = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        select(ms.age.max())
                                .from(ms)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(40);

    }

    /**
     * 나이가 평균 이상인 회원
     *
     * @throws Exception
     */
    @Test
    public void subQueryGoe() throws Exception {

        QMember ms = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        select(ms.age.avg())
                                .from(ms)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(30, 40);

    }

    @Test
    public void subQueryIn() throws Exception { // where subquery

        QMember ms = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        select(ms.age)
                                .from(ms)
                                .where(ms.age.gt(10))
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);

    }

    @Test
    public void selectSubQuery() throws Exception { // select subquery
        QMember ms = new QMember("memberSub");
        List<Tuple> result = queryFactory
                .select(member.username,
                        select(ms.age.avg())
                                .from(ms)
                ).from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }

    }

    /**
     * 김연한 왈 : 개인적으로 DB는 최소한에 데이터를 줄이는 일만 하고, 데이터를 변환하는 것은 프로그램에서 해결 하는걸로 하는게 좋음.
     *
     * @throws Exception
     */
    @Test
    public void basicCase() throws Exception {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타")
                )
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * 김연한 왈 : 개인적으로 DB는 최소한에 데이터를 줄이는 일만 하고, 데이터를 변환하는 것은 프로그램에서 해결 하는걸로 하는게 좋음.
     *
     * @throws Exception
     */
    @Test
    public void complexCase() throws Exception {
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21살~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void constant() throws Exception {
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void concat() throws Exception {
        // {username}_{age}
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue())) // stringValue 쓸일이 많다고 함. ENUM 처리할때 많이 사용한다.
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();
        for (String s : result) {
            System.out.println("result = " + result);
        }

    }

    @Test
    public void simpleProjection() throws Exception {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void toupleProjection() throws Exception {
        List<Tuple> result = queryFactory // controller 까지 하부 구조를 보여주는 건 좋은 구조가 아니다.
                .select(member.username, member.age) // 원하는 값만 찝어서 가져오는 것이 프로젝션 이다.
                .from(member)
                .fetch();
        for (Tuple tuple : result) {
            String s = tuple.get(member.username);
            Integer integer = tuple.get(member.age);
            System.out.println("username = " + s);
            System.out.println("age = " + integer);
        }
    }

    @Test
    public void findDtoByJPQL() throws Exception {
        // 패키지명 다 적어야 되서 별로다.
        // 생성자 방식만 지원한다.
        List<MemberDTO> resultList = em.createQuery("select new study.querydsl.dto.MemberDTO(m.username, m.age) from Member m", MemberDTO.class).getResultList();
        for (MemberDTO memberDTO : resultList) {
            System.out.println("memberDTO = " + memberDTO);
        }
    }

    /**
     * setter 함수를 만들어야 가져온다.
     *
     * @throws Exception
     */
    @Test
    public void findDtoBySetter() throws Exception {

        // at com.querydsl.core.types.QBean.newInstance(QBean.java:246)
        List<MemberDTO> result = queryFactory
                .select(Projections.bean(MemberDTO.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDTO memberDTO : result) {
            System.out.println("memberDTO = " + memberDTO);
        }
    }

    /**
     * field에 직접 넣어서 가져온다.
     *
     * @throws Exception
     */
    @Test
    public void findDtoByField() throws Exception {

        // at com.querydsl.core.types.QBean.newInstance(QBean.java:246)
        List<MemberDTO> result = queryFactory
                .select(Projections.fields(MemberDTO.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDTO memberDTO : result) {
            System.out.println("memberDTO = " + memberDTO);
        }
    }

    /**
     * 생성자에 직접 넣어서 가져온다.
     *
     * @throws Exception
     */
    @Test
    public void findDtoByConstructor() throws Exception {

        // at com.querydsl.core.types.QBean.newInstance(QBean.java:246)
        List<MemberDTO> result = queryFactory
                .select(Projections.constructor(MemberDTO.class,
                        member.username,
                        member.age,
                member.id)) // id를 넣으면 런타임 오류가 난다.
                .from(member)
                .fetch();

        for (MemberDTO memberDTO : result) {
            System.out.println("memberDTO = " + memberDTO);
        }
    }

    @Test
    public void findUserDto() throws Exception {

        QMember ms = new QMember("ms");

        List<UserDTO> result = queryFactory
                .select(Projections.fields(UserDTO.class,
                        member.username.as("name"), // 필드값이 Member 클래스와 DTO 클래스 이름이 다를때 as 사용해야 함.
                        ExpressionUtils.as(JPAExpressions
                                .select(ms.age.max())
                                .from(ms), "age") // 서브쿼리 사용할때 필드 매핑 방법
                ))
                .from(member)
                .fetch();

        for (UserDTO userDTO : result) {
            System.out.println("userDTO = " + userDTO);
        }
    }

    /**
     * 단점: Q파일을 생성해야 한다.
     *      queryDSL 의존성이 생긴다. [서비스, 콘트롤러, rest 까지 쓰게 된다.], DTO가 순수하지 않음.
     *
     * DTO를 깔끔하게 가고 싶다 하면 쓰지 말것.
     * 유연하게 가져가고 싶으면 써라. - querydsl 하부기술이 바뀌지 않을꺼야! 하면 쓸것
     * @throws Exception
     */
    @Test
    public void findDtoByQueryProjection() throws Exception {
        List<MemberDTO> fetch = queryFactory
                .select(new QMemberDTO(member.username, member.age)) // id 추가 컴파일 오류로 잡을 수 있다.
                .from(member)
                .fetch();
        for (MemberDTO memberDTO : fetch) {
            System.out.println("memberDTO = " + memberDTO);
        }
    }

    @Test
    public void dynamicQuery_BooleanBuilder() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {
        BooleanBuilder builder = new BooleanBuilder();
        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }
        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    @Test
    public void dynamicQuery_WhereParam() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
//                .where(usernameEq(usernameCond), ageEq(ageCond))
                .where(allEq(usernameCond, ageCond)) // 조립이 가능하다. 합성해서 처리할 수 있다.
                .fetch();
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond == null ? null : member.username.eq(usernameCond);
    }

    private Predicate allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    @Test
//    @Commit // 테스트 할려면 켜라.
    public void bulkUpdate() throws Exception {

        // member1 = 10 -> 비회원
        // member2 = 20 -> 비회원
        // member3 = 30 -> 유지
        // member4 = 40 -> 유지

        // 영향 받은 카운트
        long count = queryFactory
                .update(member)
                .set(member.username, "비회원") // 영속성 컨텍스트 무시하고 DB에 바로 쿼리를 보내 값을 바꿔버림. => 벌크연산
                .where(member.age.lt(28))
                .execute();

        // 벌크연산이 나가면 무조건 영속성컨텍스트를 초기화 해라.
        em.flush();
        em.clear();
        // DB에서 select를 가져와서 영속성 컨텍스트가 값이 달라도 영속성 컨텍스트가 우선이다.
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();
        for (Member fetch1 : fetch) {
            System.out.println("member : " + fetch);
        }

    }

    @Test
    public void buldAdd() throws Exception {
        // add
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(1))
                .execute();

        // 곱하기
        long cnt = queryFactory
                .update(member)
                .set(member.age, member.age.multiply(2))
                .execute();
    }

    @Test
    public void buldDelete() throws Exception {
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }

    @Test
    public void sqlFunction() throws Exception {
        List<String> result = queryFactory
                .select(Expressions.stringTemplate(
                        "function('replace', {0}, {1}, {2})"
                        , member.username, "member", "M"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void sqlFunction2() throws Exception {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
//                .where(member.username.eq(Expressions.stringTemplate("function('lower', {0})", member.username)))
                .where(member.username.eq(member.username.lower())) // ansi 표준 함수는 모두 하이버네이트에 등록되어 있다.
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

}
