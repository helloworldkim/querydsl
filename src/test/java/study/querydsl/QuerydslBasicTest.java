package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
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
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDTO;
import study.querydsl.dto.QMemberDTO;
import study.querydsl.dto.UserDTO;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import java.util.List;

import static com.querydsl.jpa.JPAExpressions.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.result.StatusResultMatchersExtensionsKt.isEqualTo;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.*;

@SpringBootTest
@Transactional
class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    void init() {
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
    void startJPAL() {
        String query = "select m from Member  m where m.username = :username";
        Member findMember = em.createQuery(query, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");

    }

    @Test
    void startQuerydsl() {

        Member findMember = queryFactory
                                    .select(member)
                                    .from(member)
                                    .where(member.username.eq("member1"))
                                    .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");

    }

    @Test
    void search() {

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1").and(member.age.eq(10)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");

    }

    @Test
    void searchAndParam() {

        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1"),
                        (member.age.eq(10))
                )
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");

    }

    /**
     * ?????? ??????
     */
    @Test
    void resultFetch() {

        List<Member> members = queryFactory
                .selectFrom(member)
                .fetch();

        System.out.println("members = " + members);

        Member fetchOne = queryFactory
                .selectFrom(member)
                .fetchFirst();

        QueryResults<Member> results = queryFactory.selectFrom(member).fetchResults();
        results.getTotal();
        List<Member> content = results.getResults();


        long total = queryFactory
                .selectFrom(member)
                .fetchCount();

    }

    /**
     * ??????????????????
     * 1. ?????? ?????? ????????????(desc)
     * 2. ?????? ?????? ????????????(asc)
     * ??? 2?????? ??????????????? ????????? ???????????? ??????(nulls last)
     */
    @Test
    void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> fetch = queryFactory.selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = fetch.get(0);
        Member member6 = fetch.get(1);
        Member memberNull = fetch.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();

    }

    @Test
    void paging1() {

        List<Member> fetch = queryFactory.selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(fetch.size()).isEqualTo(2);

    }

    @Test
    void paging2() {

        QueryResults<Member> results = queryFactory.selectFrom(member)
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
    void aggregation() {
        List<Tuple> result = queryFactory.select(
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
     * ?????? ????????? ??? ?????? ????????????
     */
    @Test
    void group() {

        List<Tuple> result = queryFactory.select(team.name, member.age.avg())
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

    /**
     * ???A??? ????????? ?????? ??????
     */
    @Test
    void join() {

        List<Member> teamA = queryFactory.selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();




    }

    @Test
    void ????????????() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        List<Member> fetch = queryFactory.select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

    }


    /**
     * ????????? ?????? ??????????????? ???????????? teamA??? ?????? ??????, ????????? ?????? ??????
     * JPQL : select m, t from Member m left join m.team t on t.name = 'teamA'
     */
    @Test
    void join_on_filter() {

        List<Tuple> result = queryFactory.select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);

        }

    }

    @Test
    void join_on_no_relation() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));
        List<Tuple> result = queryFactory.select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    void fetchJoinNo() {
        em.flush();
        em.clear();

        Member findMember = queryFactory.selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());

        assertThat(loaded).as("?????? ?????? ?????????").isFalse();
//        Team team1 = findMember.getTeam();
//        System.out.println("team1 = " + team1);
    }

    @Test
    void fetchJoinUse() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());

        assertThat(loaded).as("?????? ?????? ??????").isTrue();
//        Team team1 = findMember.getTeam();
//        System.out.println("team1 = " + team1);
    }

    /**
     * ????????? ?????? ?????? ????????? ??????
     */
    @Test
    void subQuery() {
        QMember memberSub = new QMember("memberSub");
        Member findMember = queryFactory.selectFrom(member)
                .where(member.age.eq(
                        select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetchOne();

        assertThat(findMember.getAge()).isEqualTo(40);
    }

    /**
     * ????????? ?????? ????????? ??????
     */
    @Test
    void subQueryGoe() {
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory.selectFrom(member)
                .where(member.age.goe(
                        select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }
        assertThat(result.get(0).getAge()).isEqualTo(30);
        assertThat(result.get(1).getAge()).isEqualTo(40);
    }


    @Test
    void subQueryIn() {
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory.selectFrom(member)
                .where(member.age.in(
                        select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }
        assertThat(result.get(0).getAge()).isEqualTo(20);
        assertThat(result.get(1).getAge()).isEqualTo(30);
        assertThat(result.get(2).getAge()).isEqualTo(40);
    }

    // JPA, QueryDsl ?????? from????????? sub????????? ?????????..!
    @Test
    void selectSubQuery() {
        QMember memberSub = new QMember("memberSub");
        List<Tuple> result = queryFactory.select(member.username,
                        select(memberSub.age.avg())
                                .from(memberSub)
                )
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }

    }

    @Test
    void basicCase() {
        List<String> result = queryFactory.select(member.age
                        .when(10).then("??????")
                        .when(20).then("?????????")
                        .otherwise("??????"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }


    /**
     * ????????? ????????????
     */
    @Test
    void complexCase() {
        List<String> result = queryFactory.select(
                    new CaseBuilder()
                            .when(member.age.between(0,20)).then("0~20???")
                            .when(member.age.between(21,30)).then("21~30???")
                        .otherwise("??????"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
    @Test
    void constant() {
        List<Tuple> result = queryFactory.select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }

    }

    @Test
    void concat() {
        List<String> result = queryFactory.select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    void simpleProjection() {

        List<String> result = queryFactory.select(member.username)
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }

    }

    @Test
    void tupleProjection() {

        List<Tuple> result = queryFactory.select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple.get(member.username) = " + tuple.get(member.username));
            System.out.println("tuple.get(member.age) = " + tuple.get(member.age));
        }


    }

    @Test
    void findDtoByJPQL() {
        List<MemberDTO> resultList = em.createQuery("select new study.querydsl.dto.MemberDTO(m.username, m.age) from Member m", MemberDTO.class).getResultList();
        for (MemberDTO o : resultList) {
            System.out.println("o = " + o);
        }
    }

    @Test
    void findDtoBySetter() {

        List<MemberDTO> fetch = queryFactory.select(Projections.bean(MemberDTO.class, member.username, member.age))
                .from(member)
                .fetch();
        for (MemberDTO memberDTO : fetch) {
            System.out.println("memberDTO = " + memberDTO);
        }

    }

    @Test
    void findDtoByField() {

        List<MemberDTO> fetch = queryFactory.select(Projections.fields(MemberDTO.class, member.username, member.age))
                .from(member)
                .fetch();
        for (MemberDTO memberDTO : fetch) {
            System.out.println("memberDTO = " + memberDTO);
        }

    }


    @Test
    void findDtoByConstructor() {

        List<MemberDTO> fetch = queryFactory.select(Projections.constructor(MemberDTO.class, member.username, member.age))
                .from(member)
                .fetch();
        for (MemberDTO memberDTO : fetch) {
            System.out.println("memberDTO = " + memberDTO);
        }

    }

    @Test
    void findUserDtoByField() {

        List<UserDTO> fetch = queryFactory.select(Projections.fields(UserDTO.class, member.username.as("name"), member.age))
                .from(member)
                .fetch();
        for (UserDTO userDTO : fetch) {
            System.out.println("userDTO = " + userDTO);
        }

    }

    @Test
    void findDtoByQueryProjection() {

        List<MemberDTO> fetch = queryFactory
                .select(new QMemberDTO(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDTO memberDTO : fetch) {
            System.out.println("memberDTO = " + memberDTO);
        }

    }

    @Test
    void dynamicQueryBooleanBuilder() {
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

        return queryFactory.select(member)
                .from(member)
                .where(builder)
                .fetch();
    }

    @Test
    void dynamicQueryWhereParam() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);

    }

    private List<Member> searchMember2(String usernameParam, Integer ageParam) {

        return queryFactory.select(member)
                .from(member)
                .where(usernameEq(usernameParam), ageEq(ageParam))
                .fetch();
    }

    private static BooleanExpression ageEq(Integer ageParam) {
        if (ageParam == null) {
            return null;
        }
        return member.age.eq(ageParam);
    }

    private static BooleanExpression usernameEq(String usernameParam) {
        if (usernameParam == null) {
            return null;
        }
        return member.username.eq(usernameParam);
    }

    @Test
    void bulkUpdate() {

        //member1 = 10 -->?????????
        //member2 = 20 -->?????????
        //member3 = 30 -->??????
        //member4 = 40 -->??????
        long count = queryFactory.update(member)
                .set(member.username, "?????????")
                .where(member.age.lt(28))
                .execute();

        em.flush();
        em.clear();

        List<Member> fetch = queryFactory.select(member)
                .from(member)
                .fetch();
        for (Member member : fetch) {
            System.out.println("member = " + member);
        }
    }

    @Test
    void bulkAdd() {

        //member1 = 10 -->?????????
        //member2 = 20 -->?????????
        //member3 = 30 -->??????
        //member4 = 40 -->??????
        long count = queryFactory.update(member)
                .set(member.age, member.age.add(1))
                .execute();

        em.flush();
        em.clear();

    }

    @Test
    void bulkMultiply() {
        long count = queryFactory.update(member)
                .set(member.age, member.age.multiply(2))
                .execute();

        em.flush();
        em.clear();

    }

    @Test
    void bulkDelete() {

        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();

        em.flush();
        em.clear();

    }

    @Test
    void sqlFunctions() {
        List<String> result = queryFactory.select(Expressions.stringTemplate("function('replace', {0},{1},{2})",
                        member.username, "member", "M"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }



}
