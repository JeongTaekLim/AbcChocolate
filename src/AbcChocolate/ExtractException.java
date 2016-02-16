package AbcChocolate;

public class ExtractException extends Exception {
	
	private String message;

	public ExtractException(String _message) {
		this.message = _message;
	}
	
	@Override
	public String getMessage() {
		// TODO Auto-generated method stub
		return super.getMessage();
	}
	
}
