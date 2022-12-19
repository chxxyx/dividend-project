package com.chxxyx.dividendproject.service;

import com.chxxyx.dividendproject.exception.impl.NoCompanyException;
import com.chxxyx.dividendproject.model.Company;
import com.chxxyx.dividendproject.model.ScrapedResult;
import com.chxxyx.dividendproject.persist.CompanyRepository;
import com.chxxyx.dividendproject.persist.DividendRepository;
import com.chxxyx.dividendproject.persist.entity.CompanyEntity;
import com.chxxyx.dividendproject.persist.entity.DividendEntity;
import com.chxxyx.dividendproject.scraper.Scraper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.Trie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Service
@AllArgsConstructor
public class CompanyService {

	private final Trie trie;
	private final Scraper yahooFinanceScraper;
	private final CompanyRepository companyRepository;
	private final DividendRepository dividendRepository;

	public Company save(String ticker) {
		boolean exists = this.companyRepository.existsByTicker(ticker); // 회사 존재 여부
		if (exists) {
			throw new RuntimeException("already exists ticker -> " + ticker);
		}
		return this.storeCompanyAndDividend(ticker);
	}

	public Page<CompanyEntity> getAllCompany(Pageable pageable){
		return this.companyRepository.findAll(pageable);
	}

	private Company storeCompanyAndDividend(String ticker) {

		// ticker를 기준으로 회사를 스크래핑
		Company company = this.yahooFinanceScraper.scrapCompanyByTicker(ticker);
		if (ObjectUtils.isEmpty(company)) {
			throw  new RuntimeException("failed to scrap ticker -> " + ticker);
		}

		// 해당 회사가 존재할 경우, 회사의 배당금 정보를 스크래핑
		ScrapedResult scrapedResult = this.yahooFinanceScraper.scrap(company);

		// 스크래핑 결과
		CompanyEntity companyEntity = this.companyRepository.save(new CompanyEntity(company));
		List<DividendEntity> dividendEntityList= scrapedResult.getDividends().stream()
														.map(e -> new DividendEntity(companyEntity.getId(), e))
														.collect(Collectors.toList());
		this.dividendRepository.saveAll(dividendEntityList);
		return company;
	}

	public List<String> getCompanyNamesByKeyword(String keyword) {
		Pageable limit = PageRequest.of(0, 10);
		Page<CompanyEntity> companyEntities = this.companyRepository.findAllByNameStartingWithIgnoreCase(keyword, limit);
		return companyEntities.stream()
								.map(e -> e.getName())
								.collect(Collectors.toList());
	}

	public void addAutocompleteKeyword(String keyword) {
		this.trie.put(keyword, null);
	}

	public List<String> autocomplete(String keyword) {
		return (List<String>) this.trie.prefixMap(keyword).keySet()
			.stream().collect(Collectors.toList());
	}

	public void deleteAutocompleteKeyword(String keyword) {
		this.trie.remove(keyword);
	}

	public String deleteCompany(String ticker) {

		var company = this.companyRepository.findByTicker(ticker)
			.orElseThrow(() -> new NoCompanyException());

		this.dividendRepository.deleteAllByCompanyId(company.getId());
		this.companyRepository.delete(company);
		this.deleteAutocompleteKeyword(company.getName());

		return company.getName();
	}
}
