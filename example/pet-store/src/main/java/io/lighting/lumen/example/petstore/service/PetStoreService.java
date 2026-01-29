package io.lighting.lumen.example.petstore.service;

import io.lighting.lumen.example.petstore.model.Owner;
import io.lighting.lumen.example.petstore.model.Pet;
import io.lighting.lumen.example.petstore.repo.OwnerRepository;
import io.lighting.lumen.example.petstore.repo.PetRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@Service
public class PetStoreService {

    private final PetRepository petRepository;
    private final OwnerRepository ownerRepository;

    public PetStoreService(PetRepository petRepository, OwnerRepository ownerRepository) {
        this.petRepository = petRepository;
        this.ownerRepository = ownerRepository;
    }

    // Pet operations
    public List<Pet> getAllPets() throws SQLException {
        return petRepository.findAll();
    }

    public Pet getPet(Long id) throws SQLException {
        return petRepository.findById(id).orElse(null);
    }

    public List<Pet> getAvailablePets() throws SQLException {
        return petRepository.findAvailable();
    }

    public List<Pet> getPetsBySpecies(String species) throws SQLException {
        return petRepository.findBySpecies(species);
    }

    public Pet addPet(String name, String species, String breed, int age, BigDecimal price) throws SQLException {
        Pet pet = new Pet();
        pet.setName(name);
        pet.setSpecies(species);
        pet.setBreed(breed);
        pet.setAge(age);
        pet.setPrice(price);
        pet.setBirthDate(LocalDate.now().minusYears(age));
        pet.setAvailable(true);
        return petRepository.create(pet);
    }

    public boolean updatePet(Pet pet) throws SQLException {
        return petRepository.update(pet);
    }

    public boolean deletePet(Long id) throws SQLException {
        return petRepository.delete(id);
    }

    public int getAvailableCount() throws SQLException {
        return petRepository.countAvailable();
    }

    // Owner operations
    public List<Owner> getAllOwners() throws SQLException {
        return ownerRepository.findAll();
    }

    public Owner getOwner(Long id) throws SQLException {
        return ownerRepository.findById(id).orElse(null);
    }

    public Owner addOwner(String name, String email, String phone, String address) throws SQLException {
        Owner owner = new Owner();
        owner.setName(name);
        owner.setEmail(email);
        owner.setPhone(phone);
        owner.setAddress(address);
        return ownerRepository.create(owner);
    }

    public boolean updateOwner(Owner owner) throws SQLException {
        return ownerRepository.update(owner);
    }

    public boolean deleteOwner(Long id) throws SQLException {
        return ownerRepository.delete(id);
    }

    // Initialize sample data
    public void initializeSampleData() throws SQLException {
        if (petRepository.findAll().isEmpty()) {
            // Add sample pets
            addPet("Fluffy", "Cat", "Persian", 2, new BigDecimal("599.99"));
            addPet("Buddy", "Dog", "Golden Retriever", 3, new BigDecimal("899.99"));
            addPet("Whiskers", "Cat", "Siamese", 1, new BigDecimal("449.99"));
            addPet("Max", "Dog", "German Shepherd", 4, new BigDecimal("799.99"));
            addPet("Tweety", "Bird", "Canary", 2, new BigDecimal("99.99"));
            addPet("Goldie", "Fish", "Goldfish", 1, new BigDecimal("29.99"));
            addPet("Hoppy", "Rabbit", "Dutch", 1, new BigDecimal("79.99"));
            addPet("Speedy", "Hamster", "Syrian", 1, new BigDecimal("39.99"));
        }

        if (ownerRepository.findAll().isEmpty()) {
            // Add sample owners
            addOwner("John Smith", "john@example.com", "555-0101", "123 Main St");
            addOwner("Jane Doe", "jane@example.com", "555-0102", "456 Oak Ave");
            addOwner("Bob Wilson", "bob@example.com", "555-0103", "789 Pine Rd");
        }
    }
}
