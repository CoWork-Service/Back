package hack.backend.domain.example.dto.response;

import hack.backend.domain.example.entity.ExampleEntity;

public record ExampleResponse(
	Long id,
	String name
) {
	public static ExampleResponse from(ExampleEntity example) {
		return new ExampleResponse(
			example.getId(),
			example.getName()
		);
	}
}
