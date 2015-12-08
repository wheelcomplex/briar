package org.briarproject.crypto;

import org.briarproject.BriarTestCase;
import org.briarproject.TestSeedProvider;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.system.SeedProvider;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class KeyAgreementTest extends BriarTestCase {

	@Test
	public void testKeyAgreement() throws Exception {
		SeedProvider seedProvider = new TestSeedProvider();
		CryptoComponent crypto = new CryptoComponentImpl(seedProvider);
		KeyPair aPair = crypto.generateAgreementKeyPair();
		byte[] aPub = aPair.getPublic().getEncoded();
		KeyPair bPair = crypto.generateAgreementKeyPair();
		byte[] bPub = bPair.getPublic().getEncoded();
		byte[] aSecret = crypto.deriveMasterSecret(aPub, bPair, true);
		byte[] bSecret = crypto.deriveMasterSecret(bPub, aPair, false);
		assertArrayEquals(aSecret, bSecret);
	}
}
