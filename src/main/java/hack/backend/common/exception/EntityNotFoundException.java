package hack.backend.common.exception;

public class EntityNotFoundException extends AppException {

	public EntityNotFoundException(ErrorCode errorCode) {
		super(errorCode);
	}
}