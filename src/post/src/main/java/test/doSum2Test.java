package test;
import static org.junit.Assert.*;

import org.junit.Assert;
import org.junit.Test;

import rest.post.App;

/**
 * 
 */

/**
 * @author suil
 *
 */
public class doSum2Test {

	@Test
	public void simpleTestSum() {
		System.out.println("shouldFail");
    	Assert.assertEquals(App.doSum(6,6), 12);
    }

}
