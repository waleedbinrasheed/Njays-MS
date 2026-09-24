package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.master.MasterDataDtos.CategoryRequest;
import com.inventory.dto.master.MasterDataDtos.CategoryResponse;
import com.inventory.entity.Category;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.MasterDataMapper;
import com.inventory.repository.CategoryRepository;
import com.inventory.repository.ProductRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.inventory.service.MasterDataSupport.checkVersion;
import static com.inventory.service.MasterDataSupport.inUse;
import static com.inventory.service.MasterDataSupport.searchTerm;
import static com.inventory.service.MasterDataSupport.trimToNull;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> search(String search, Boolean active, Pageable pageable) {
        return PageResponse.of(categories.search(searchTerm(search), active, pageable), MasterDataMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return MasterDataMapper.toResponse(load(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw duplicate(name);
        }
        Category category = new Category();
        apply(category, request, name);
        return MasterDataMapper.toResponse(categories.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = load(id);
        checkVersion(category, request.version());
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicate(name);
        }
        apply(category, request, name);
        return MasterDataMapper.toResponse(categories.saveAndFlush(category));
    }

    @Transactional
    public void delete(Long id) {
        Category category = load(id);
        if (products.countByCategoryId(id) > 0) {
            throw inUse("Category '" + category.getName() + "'");
        }
        categories.delete(category);
    }

    private void apply(Category c, CategoryRequest r, String name) {
        c.setName(name);
        c.setDescription(trimToNull(r.description()));
        c.setActive(r.active() == null || r.active());
    }

    private Category load(Long id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private static BusinessException duplicate(String name) {
        return new BusinessException(HttpStatus.CONFLICT, "A category named '" + name + "' already exists");
    }
}
