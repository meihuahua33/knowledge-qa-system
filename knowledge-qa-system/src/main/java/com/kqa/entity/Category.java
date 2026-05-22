package com.kqa.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;            // 分类名称: 技术文档/管理制度/产品手册/培训材料

    @Column(length = 500)
    private String description;

    @Column(name = "parent_id")
    private Long parentId;          // 父分类ID, 支持二级分类
}
