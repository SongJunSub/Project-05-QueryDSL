package education.querydsl;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import education.querydsl.entity.Member;
import education.querydsl.entity.QMember;
import education.querydsl.entity.QTeam;
import education.querydsl.entity.Team;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.querydsl.jpa.JPAExpressions.*;
import static education.querydsl.entity.QMember.*;
import static education.querydsl.entity.QTeam.team;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before(){

        queryFactory = new JPAQueryFactory(em);

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
    public void startJPQL(){

        //Member1을 찾아라
        Member findMember = em.createQuery("SELECT m FROM Member m WHERE m.username = :username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");

    }

    @Test
    public void startQueryDSL(){

        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");

    }

    @Test
    public void search(){

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.between(10, 30)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");

    }

    @Test
    public void searchAndParam(){

        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1"),
                        member.age.eq(10)
                )
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");

    }

    @Test
    public void resultFetch(){

        /*List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();*/

        /*Member fetchOne = queryFactory
                .selectFrom(member)
                .fetchOne();*/

        /*Member fetchFirst = queryFactory
                .selectFrom(member)
                //.limit(1).fetchOne()와 동일
                .fetchFirst();*/

        /*//페이징 정보 포함, Total Count 쿼리 추가 실행
        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults();

        results.getTotal();
        List<Member> content = results.getResults();*/

        //Count 쿼리로 변경해서 Count 수 조회
        long total = queryFactory
                .selectFrom(member)
                .fetchCount();

    }

    /*
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순 (DESC)
     * 2. 회원 이름 오름차순 (ASC)
     * 단, 2에서 회원 이름이 없으면 마지막에 출력 (Nulls Last)
     */
    @Test
    public void sort(){

        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> results = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = results.get(0);
        Member member6 = results.get(1);
        Member memberNull = results.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();

    }

    @Test
    public void paging1(){

        List<Member> results = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(results.size()).isEqualTo(2);

    }

    @Test
    public void paging2(){

        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertThat(results.getTotal()).isEqualTo(4);
        assertThat(results.getLimit()).isEqualTo(2);
        assertThat(results.getOffset()).isEqualTo(1);
        assertThat(results.getResults().size()).isEqualTo(2);

    }

    @Test
    public void aggregation(){

        List<Tuple> result = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);

        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);

    }

    /*
     * 팀 이름과 각 팀의 평균 연령을 구해라.
     */
    @Test
    public void group() throws Exception {

        List<Tuple> result = queryFactory
                .select(
                        team.name,
                        member.age.avg()
                )
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);
        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);

    }

    /*
     * 팀 A에 소속된 모든 회원을 찾아라.
     */
    @Test
    public void join(){

        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result).extracting("username").containsExactly("member1", "member2");

    }

    /*
     * 세타 조인 (연관 관계가 없는 조인)
     * (Left Outer Join, Right Outer Join 불가, on절을 사용하면 외부 조인 가능)
     * 회원의 이름이 팀 이름과 같은 회원을 조회
     */
    @Test
    public void thetaJoin(){

        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");

    }

    /*
     * 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL : SELECT m, t FROM Member m LEFT JOIN m.team t ON t.name = "teamA"
     */
    @Test
    public void joinOnFiltering(){

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();

        for(Tuple tuple : result){
            System.out.println("Tuple : " + tuple);
        }

    }

    /*
     * 연관관계가 없는 엔티티 외부 조인
     * 회원의 이름이 팀 이름과 같은 대상 외부 조인
     */
    @Test
    public void joinOnNoRelataion(){

        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();

        for(Tuple tuple : result){
            System.out.println("Tuple : " + tuple);
        }

    }

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoinNo(){

        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());

        assertThat(loaded).as("Fetch Join 미적용").isFalse();

    }

    @Test
    public void fetchJoinUse(){

        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());

        assertThat(loaded).as("Fetch Join 적용").isTrue();

    }

    /*
     * 나이가 가장 많은 회원을 서브 쿼리를 이용하여 조회
     */
    @Test
    public void subQuery(){

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age").containsExactly(40);

    }

    /*
     * 나이가 평균 이상인 회원을 서브 쿼리를 이용하여 조회
     */
    @Test
    public void subQueryGoe(){

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age").containsExactly(30, 40);

    }

    /*
     * 나이가 평균 이상인 회원을 서브 쿼리를 이용하여 조회
     */
    @Test
    public void subQueryIn(){

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        assertThat(result).extracting("age").containsExactly(20, 30, 40);

    }

    @Test
    public void selectSubQuery(){

        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = queryFactory
                .select(
                        member.username,
                        select(memberSub.age.avg()).from(memberSub)
                )
                .from(member)
                .fetch();

        for(Tuple tuple : result){
            System.out.println("Tuple : " + tuple);
        }

    }

    @Test
    public void basicCase(){

        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타")
                )
                .from(member)
                .fetch();

        for(String s : result){
            System.out.println("s : " + s);
        }

    }

    @Test
    public void complexCase(){

        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for(String s : result){
            System.out.println("s : " + s);
        }

    }

    @Test
    public void constant(){

        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for(Tuple tuple : result){
            System.out.println("tuple : " + tuple);
        }

    }

    @Test
    public void concat(){

        //{username}_{age}
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for(String s : result){
            System.out.println("s : " + s);
        }

    }

}