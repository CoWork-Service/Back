package hack.backend.domain.example.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hack.backend.common.exception.EntityNotFoundException;
import hack.backend.common.exception.ErrorCode;
import hack.backend.domain.example.dao.ExampleRepository;
import hack.backend.domain.example.dto.response.ExampleResponse;
import hack.backend.domain.example.entity.ExampleEntity;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class ExampleService {

	private final ExampleRepository exampleRepository;

	@Transactional
	public Long save(String name) {
		ExampleEntity example = ExampleEntity.builder()
			.name(name)
			.build();
		return exampleRepository.save(example).getId();
	}

	public ExampleResponse findById(Long id) {
		return ExampleResponse.from(
			exampleRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException(ErrorCode.NOT_FOUND))
		);
	}
}