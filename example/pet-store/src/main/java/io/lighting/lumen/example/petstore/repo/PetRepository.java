package io.lighting.lumen.example.petstore.repo;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.db.Query;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.dsl.Table;
import io.lighting.lumen.example.petstore.model.Pet;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.ast.DeleteStmt;
import io.lighting.lumen.sql.ast.InsertStmt;
import io.lighting.lumen.sql.ast.SelectStmt;
import io.lighting.lumen.sql.ast.UpdateStmt;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class PetRepository {

    private final Db db;
    private final Dsl dsl;
    private final Lumen lumen;

    public PetRepository(Lumen lumen) {
        this.lumen = lumen;
        this.db = lumen.db();
        this.dsl = lumen.dsl();
    }

    // ========== DSL Pattern Examples ==========

    /**
     * Find all pets using DSL.
     */
    public List<Pet> findAll() throws SQLException {
        var t = dsl.table(Pet.class).as("p");
        SelectStmt stmt = dsl.select(
                Dsl.item(t.col(Pet::getId).expr()),
                Dsl.item(t.col(Pet::getName).expr()),
                Dsl.item(t.col(Pet::getSpecies).expr()),
                Dsl.item(t.col(Pet::getBreed).expr()),
                Dsl.item(t.col(Pet::getAge).expr()),
                Dsl.item(t.col(Pet::getPrice).expr()),
                Dsl.item(t.col(Pet::getAvailable).expr())
            )
            .from(t)
            .build();
        return db.fetch(Query.of(stmt, Bindings.empty()), Pet.class);
    }

    /**
     * Find pet by ID using DSL.
     */
    public Optional<Pet> findById(Long id) throws SQLException {
        var t = dsl.table(Pet.class).as("p");
        SelectStmt stmt = dsl.select(Dsl.item(t.col(Pet::getId).expr()))
            .from(t)
            .where(w -> w.and(t.col(Pet::getId).eq(id)))
            .build();
        List<Pet> results = db.fetch(Query.of(stmt, Bindings.empty()), Pet.class);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Find available pets using DSL.
     */
    public List<Pet> findAvailable() throws SQLException {
        var t = dsl.table(Pet.class).as("p");
        SelectStmt stmt = dsl.select(
                Dsl.item(t.col(Pet::getId).expr()),
                Dsl.item(t.col(Pet::getName).expr()),
                Dsl.item(t.col(Pet::getSpecies).expr()),
                Dsl.item(t.col(Pet::getBreed).expr()),
                Dsl.item(t.col(Pet::getPrice).expr())
            )
            .from(t)
            .where(w -> w.and(t.col(Pet::getAvailable).eq(true)))
            .build();
        return db.fetch(Query.of(stmt, Bindings.empty()), Pet.class);
    }

    /**
     * Find pets by species using DSL.
     */
    public List<Pet> findBySpecies(String species) throws SQLException {
        var t = dsl.table(Pet.class).as("p");
        SelectStmt stmt = dsl.select(Dsl.item(t.col(Pet::getId).expr()),
                Dsl.item(t.col(Pet::getName).expr()),
                Dsl.item(t.col(Pet::getSpecies).expr()),
                Dsl.item(t.col(Pet::getBreed).expr()),
                Dsl.item(t.col(Pet::getAge).expr())
            )
            .from(t)
            .where(w -> w.and(t.col(Pet::getSpecies).like("%" + species + "%")))
            .build();
        return db.fetch(Query.of(stmt, Bindings.empty()), Pet.class);
    }

    /**
     * Create a new pet using DSL.
     */
    public Pet create(Pet pet) throws SQLException {
        var t = dsl.table(Pet.class);
        InsertStmt stmt = dsl.insertInto(t)
            .columns(
                Pet::getName,
                Pet::getSpecies,
                Pet::getBreed,
                Pet::getAge,
                Pet::getPrice,
                Pet::getOwnerId,
                Pet::getBirthDate,
                Pet::getAvailable
            )
            .row(
                pet.getName(),
                pet.getSpecies(),
                pet.getBreed(),
                pet.getAge(),
                pet.getPrice(),
                pet.getOwnerId(),
                pet.getBirthDate(),
                pet.getAvailable() != null ? pet.getAvailable() : true
            )
            .build();
        RenderedSql rendered = lumen.renderer().render(stmt, Bindings.empty());
        Long id = db.executeAndReturnGeneratedKey(Command.of(rendered), "id", rs -> rs.getLong(1));
        pet.setId(id);
        return pet;
    }

    /**
     * Update pet using DSL.
     */
    public boolean update(Pet pet) throws SQLException {
        var t = dsl.table(Pet.class).as("p");
        UpdateStmt stmt = dsl.update(t)
            .set(Pet::getName, pet.getName())
            .set(Pet::getSpecies, pet.getSpecies())
            .set(Pet::getBreed, pet.getBreed())
            .set(Pet::getAge, pet.getAge())
            .set(Pet::getPrice, pet.getPrice())
            .set(Pet::getAvailable, pet.getAvailable())
            .where(t.col(Pet::getId).eq(pet.getId()))
            .build();
        RenderedSql rendered = lumen.renderer().render(stmt, Bindings.empty());
        return db.execute(Command.of(rendered)) > 0;
    }

    /**
     * Delete pet using DSL.
     */
    public boolean delete(Long id) throws SQLException {
        var t = dsl.table(Pet.class);
        DeleteStmt stmt = dsl.deleteFrom(t)
            .where(t.col(Pet::getId).eq(id))
            .build();
        RenderedSql rendered = lumen.renderer().render(stmt, Bindings.empty());
        return db.execute(Command.of(rendered)) > 0;
    }

    /**
     * Count available pets.
     */
    public int countAvailable() throws SQLException {
        var t = dsl.table(Pet.class).as("p");
        SelectStmt stmt = dsl.select(Dsl.item(t.col(Pet::getId).expr()))
            .from(t)
            .where(w -> w.and(t.col(Pet::getAvailable).eq(true)))
            .build();
        List<Pet> results = db.fetch(Query.of(stmt, Bindings.empty()), Pet.class);
        return results.size();
    }
}
