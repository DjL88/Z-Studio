const functions = require('firebase-functions');
const admin = require('firebase-admin');
const axios = require('axios');

admin.initializeApp();

// Replicate API Configuration
// Users should set this in their firebase functions config:
// firebase functions:config:set replicate.token="YOUR_REPLICATE_API_TOKEN"
// Or use standard process.env.REPLICATE_API_TOKEN
const REPLICATE_TOKEN = process.env.REPLICATE_API_TOKEN || functions.config().replicate?.token;

/**
 * Cloud Function to offload AI Denoise to Replicate using the NAFNet model.
 * Accepts: { imageBase64: "...", imageUrl: "..." }
 * Returns: { denoisedImageUrl: "..." }
 */
exports.denoiseImage = functions.https.onRequest(async (req, res) => {
  // Enable CORS
  res.set('Access-Control-Allow-Origin', '*');
  if (req.method === 'OPTIONS') {
    res.set('Access-Control-Allow-Methods', 'GET, POST');
    res.set('Access-Control-Allow-Headers', 'Content-Type, Authorization');
    res.status(204).send('');
    return;
  }

  try {
    const { imageBase64, imageUrl } = req.body;
    
    let inputImage = imageUrl;

    // If a base64 string is provided, convert to a Data URI for Replicate
    if (imageBase64) {
      inputImage = `data:image/jpeg;base64,${imageBase64}`;
    }

    if (!inputImage) {
      res.status(400).json({ error: 'Missing imageBase64 or imageUrl in request body.' });
      return;
    }

    if (!REPLICATE_TOKEN) {
      res.status(500).json({ error: 'Replicate API Token is not configured on the server.' });
      return;
    }

    // NAFNet Model Version Hash for Image Denoising (megvii-research/nafnet)
    const NAFNET_VERSION = '565778d61a84cdbb3f98c39e240a12e2c2c3260849db0095efc32e01df3cf6b4';

    // 1. Trigger Replicate Prediction
    console.log('Initiating Replicate NAFNet prediction...');
    const startResponse = await axios.post(
      'https://api.replicate.com/v1/predictions',
      {
        version: NAFNET_VERSION,
        input: {
          image: inputImage,
          task_type: 'Denoising'
        }
      },
      {
        headers: {
          'Authorization': `Token ${REPLICATE_TOKEN}`,
          'Content-Type': 'application/json'
        }
      }
    );

    const predictionId = startResponse.data.id;
    let status = startResponse.data.status;
    let outputUrl = null;

    console.log(`Prediction started. ID: ${predictionId}. Initial Status: ${status}`);

    // 2. Poll the prediction status until succeeded, failed, or cancelled (max 20 seconds)
    let attempts = 0;
    while (status !== 'succeeded' && status !== 'failed' && status !== 'canceled' && attempts < 20) {
      await new Promise(resolve => setTimeout(resolve, 1000)); // wait 1s
      attempts++;

      const checkResponse = await axios.get(
        `https://api.replicate.com/v1/predictions/${predictionId}`,
        {
          headers: {
            'Authorization': `Token ${REPLICATE_TOKEN}`
          }
        }
      );

      status = checkResponse.data.status;
      console.log(`Polling Replicate. Attempt ${attempts}, Status: ${status}`);
      
      if (status === 'succeeded') {
        outputUrl = checkResponse.data.output;
        break;
      }
    }

    if (status === 'succeeded' && outputUrl) {
      console.log(`Denoise successful! Output URL: ${outputUrl}`);
      res.status(200).json({ denoisedImageUrl: outputUrl });
    } else {
      res.status(502).json({ error: `Denoising prediction failed or timed out. Status: ${status}` });
    }

  } catch (error) {
    console.error('Error in denoiseImage Cloud Function:', error.message);
    if (error.response) {
      console.error('Replicate API error output:', error.response.data);
    }
    res.status(500).json({ error: error.message || 'Internal server error.' });
  }
});
