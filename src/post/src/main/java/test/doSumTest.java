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
public class doSumTest {

	@Test
	public void simpleTestSum() {
    	Assert.assertEquals(App.doSum(2,4), 6);
    }


}
