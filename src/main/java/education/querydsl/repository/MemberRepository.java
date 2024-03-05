package education.querydsl.repository;

import education.querydsl.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {

    //SELECT m FROM Member m WHERE m.username = ?
    List<Member> findByUsername(String username);

}