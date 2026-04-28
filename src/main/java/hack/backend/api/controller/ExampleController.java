package hack.backend.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import hack.backend.api.dto.response.DefaultIdResponse;
import hack.backend.common.dto.DataResponse;
import hack.backend.domain.example.application.ExampleService;
import hack.backend.domain.example.dto.request.ExampleRequest;
import hack.backend.domain.example.dto.response.ExampleResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@RequestMapping("/api/examples")
public class ExampleController {

	private final ExampleService exampleService;

	@PostMapping
	public ResponseEntity<DataResponse<DefaultIdResponse>> save(@RequestBody ExampleRequest request) {
		return ResponseEntity.ok(
			DataResponse.created(
				DefaultIdResponse.of(exampleService.save(request.name()))
			)
		);
	}

	@GetMapping("/{id}")
	public ResponseEntity<DataResponse<ExampleResponse>> findById(@PathVariable Long id) {
		return ResponseEntity.ok(
			DataResponse.from(exampleService.findById(id))
		);
	}
}