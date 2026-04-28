package hack.backend.domain.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "example_entity")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExampleEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "example_id")
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Builder
	public ExampleEntity(String name) {
		this.name = name;
	}
}