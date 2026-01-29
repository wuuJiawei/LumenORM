package io.lighting.lumen.example.petstore.repo;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.db.Query;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.dsl.Table;
import io.lighting.lumen.example.petstore.model.Owner;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.ast.DeleteStmt;
import io.lighting.lumen.sql.ast.InsertStmt;
import io.lighting.lumen.sql.ast.SelectStmt;
import io.lighting.lumen.sql.ast.UpdateStmt;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class OwnerRepository {

    private final Db db;
    private final Dsl dsl;
    private final Lumen lumen;

    public OwnerRepository(Lumen lumen) {
        this.lumen = lumen;
        this.db = lumen.db();
        this.dsl = lumen.dsl();
    }

    public List<Owner> findAll() throws SQLException {
        var t = dsl.table(Owner.class).as("o");
        SelectStmt stmt = dsl.select(
                Dsl.item(t.col(Owner::getId).expr()),
                Dsl.item(t.col(Owner::getName).expr()),
                Dsl.item(t.col(Owner::getEmail).expr()),
                Dsl.item(t.col(Owner::getPhone).expr())
            )
            .from(t)
            .build();
        return db.fetch(Query.of(stmt, Bindings.empty()), Owner.class);
    }

    public Optional<Owner> findById(Long id) throws SQLException {
        var t = dsl.table(Owner.class).as("o");
        SelectStmt stmt = dsl.select(Dsl.item(t.col(Owner::getId).expr()))
            .from(t)
            .where(w -> w.and(t.col(Owner::getId).eq(id)))
            .build();
        List<Owner> results = db.fetch(Query.of(stmt, Bindings.empty()), Owner.class);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Owner create(Owner owner) throws SQLException {
        var t = dsl.table(Owner.class);
        owner.setCreatedAt(LocalDateTime.now());
        InsertStmt stmt = dsl.insertInto(t)
            .columns(Owner::getName, Owner::getEmail, Owner::getPhone, Owner::getAddress, Owner::getCreatedAt)
            .row(owner.getName(), owner.getEmail(), owner.getPhone(), owner.getAddress(), owner.getCreatedAt())
            .build();
        RenderedSql rendered = lumen.renderer().render(stmt, Bindings.empty());
        Long id = db.executeAndReturnGeneratedKey(Command.of(rendered), "id", rs -> rs.getLong(1));
        owner.setId(id);
        return owner;
    }

    public boolean update(Owner owner) throws SQLException {
        var t = dsl.table(Owner.class).as("o");
        UpdateStmt stmt = dsl.update(t)
            .set(Owner::getName, owner.getName())
            .set(Owner::getEmail, owner.getEmail())
            .set(Owner::getPhone, owner.getPhone())
            .set(Owner::getAddress, owner.getAddress())
            .where(t.col(Owner::getId).eq(owner.getId()))
            .build();
        RenderedSql rendered = lumen.renderer().render(stmt, Bindings.empty());
        return db.execute(Command.of(rendered)) > 0;
    }

    public boolean delete(Long id) throws SQLException {
        var t = dsl.table(Owner.class);
        DeleteStmt stmt = dsl.deleteFrom(t)
            .where(t.col(Owner::getId).eq(id))
            .build();
        RenderedSql rendered = lumen.renderer().render(stmt, Bindings.empty());
        return db.execute(Command.of(rendered)) > 0;
    }
}
