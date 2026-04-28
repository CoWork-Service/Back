package hack.backend.api.dto.response;

public record DefaultIdResponse(
	Long id
) {
	public static DefaultIdResponse of(Long id) {
		return new DefaultIdResponse(id);
	}
}