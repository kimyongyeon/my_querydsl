package study.querydsl;

import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Hello;
import study.querydsl.entity.Member;
import study.querydsl.entity.QHello;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional // 기본적으로 테이블을 만들고 지워 버림
@Commit // 테이블을 남기고 싶으면 쓴다. 롤백이 된다.
class QuerydslApplicationTests {

    @Autowired // 스프링 최신 버전
//    @PersistenceContext // 표준 스펙
    EntityManager em;

    @Test
    public void testEntity() {
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

        // 초기화
        em.flush(); // DB로 날린다.
        em.clear(); // 영속성 컨텍스트 날린다.

        List<Member> members = em.createQuery("select m from Member m", Member.class).getResultList();

        for (Member member : members) {
            System.out.println("member = " + member);
            System.out.println("-> member.team" + member.getTeam());
        }

    }


//    @Test
//    void contextLoads() {
//        Hello hello = new Hello();
//        em.persist(hello);
//
//        JPAQueryFactory query = new JPAQueryFactory(em);
//        QHello qHello = QHello.hello;
//
//        Hello hello1 = query.selectFrom(qHello)
//                .fetchOne();
//
//        assertThat(hello1).isEqualTo(hello);
//        assertThat(hello1.getId()).isEqualTo(hello.getId());
//    }

}
