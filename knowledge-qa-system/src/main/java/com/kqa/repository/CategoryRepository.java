package com.kqa.repository;

import com.kqa.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // 按名称精确查找
    Optional<Category> findByName(String name);

    // 查询子分类
    List<Category> findByParentId(Long parentId);

    // 查询所有顶级分类 (parentId IS NULL)
    List<Category> findByParentIdIsNull();
}
