package edu.university.itsm.intelligence;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface CategoryRuleRepository extends JpaRepository<CategoryRule,UUID> {
    @EntityGraph(attributePaths="category") List<CategoryRule> findByActiveTrue();
    Optional<CategoryRule> findByCategoryIdAndKeyword(UUID categoryId,String keyword);
}
