
public class Test1 {

	public static void main(String[] args)
	{
		Akanksha a1  = null;
		try
		{
			a1.a = 9;
		}
		catch(NullPointerException e)
		{
			System.out.println("Hello");
		}
		System.out.println("World");
	}
	
	
	class Akanksha
	{
		int a;
	}
	
}
