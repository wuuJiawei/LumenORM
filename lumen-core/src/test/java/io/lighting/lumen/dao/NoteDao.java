package io.lighting.lumen.dao;

import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.IdStrategy;
import io.lighting.lumen.meta.LogicDelete;
import io.lighting.lumen.meta.Table;
import io.lighting.lumen.page.PageRequest;
import io.lighting.lumen.page.PageResult;
import io.lighting.lumen.page.Sort;
import java.sql.SQLException;
import java.util.List;

public interface NoteDao {
    int insert(NoteEntity entity) throws SQLException;
    NoteEntity selectById(Long id) throws SQLException;
    List<NoteEntity> selectList() throws SQLException;
    int updateById(NoteEntity entity) throws SQLException;
    int deleteById(Long id) throws SQLException;
    PageResult<NoteEntity> selectPage(PageRequest request) throws SQLException;
}

@Table(name = "NOTES")
class NoteEntity {
    @Id(strategy = IdStrategy.AUTO)
    @Column(name = "ID")
    private Long id;

    @Column(name = "TITLE")
    private String title;

    @Column(name = "CONTENT")
    private String content;

    @LogicDelete(active = "0", deleted = "1")
    @Column(name = "DELETED")
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
