package io.lighting.lumen.example.petstore.web;

import io.lighting.lumen.example.petstore.model.Owner;
import io.lighting.lumen.example.petstore.model.Pet;
import io.lighting.lumen.example.petstore.service.PetStoreService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.sql.SQLException;
import java.math.BigDecimal;

@Controller
public class PetStoreController {

    private final PetStoreService petStoreService;

    public PetStoreController(PetStoreService petStoreService) {
        this.petStoreService = petStoreService;
    }

    @GetMapping({"/", "/pets"})
    public String pets(Model model) throws SQLException {
        petStoreService.initializeSampleData();
        model.addAttribute("pets", petStoreService.getAvailablePets());
        model.addAttribute("allPets", petStoreService.getAllPets());
        model.addAttribute("owners", petStoreService.getAllOwners());
        model.addAttribute("page", "pets");
        return "pet-store";
    }

    @GetMapping("/owners")
    public String owners(Model model) throws SQLException {
        model.addAttribute("owners", petStoreService.getAllOwners());
        model.addAttribute("page", "owners");
        return "owners";
    }

    @GetMapping("/pet/{id}")
    public String petDetail(@PathVariable Long id, Model model) throws SQLException {
        Pet pet = petStoreService.getPet(id);
        if (pet == null) {
            return "redirect:/pets";
        }
        model.addAttribute("pet", pet);
        model.addAttribute("page", "pet-detail");
        return "pet-detail";
    }

    @GetMapping("/owner/{id}")
    public String ownerDetail(@PathVariable Long id, Model model) throws SQLException {
        Owner owner = petStoreService.getOwner(id);
        if (owner == null) {
            return "redirect:/owners";
        }
        model.addAttribute("owner", owner);
        model.addAttribute("page", "owner-detail");
        return "owner-detail";
    }

    @PostMapping("/pet/add")
    public String addPet(
            @RequestParam String name,
            @RequestParam String species,
            @RequestParam String breed,
            @RequestParam int age,
            @RequestParam BigDecimal price
    ) throws SQLException {
        petStoreService.addPet(name, species, breed, age, price);
        return "redirect:/pets";
    }

    @PostMapping("/owner/add")
    public String addOwner(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String phone,
            @RequestParam String address
    ) throws SQLException {
        petStoreService.addOwner(name, email, phone, address);
        return "redirect:/owners";
    }

    @GetMapping("/pet/delete/{id}")
    public String deletePet(@PathVariable Long id) throws SQLException {
        petStoreService.deletePet(id);
        return "redirect:/pets";
    }

    @GetMapping("/owner/delete/{id}")
    public String deleteOwner(@PathVariable Long id) throws SQLException {
        petStoreService.deleteOwner(id);
        return "redirect:/owners";
    }
}
