db.ProductsReferences.insertMany([
  {
    productName: "Enterprise",
    stripeProductId: "prod_SfmbAAu7XCOLBc",
    price: {
      monthlyPriceId: "price_1RkR23GfTIjQ6vqfFVuS0F1Q",
      monthlyPrice: 449,
      annualPriceId: "price_1RkXZlGfTIjQ6vqf4WsMCeJi",
      annuallyPrice: 4308
    },
    createdAt: new Date()
  },
  {
    productName: "Professional",
    stripeProductId: "prod_SfmacJiMfMxh9P",
    price: {
      monthlyPriceId: "price_1RkR0zGfTIjQ6vqfFChiH9l8",
      monthlyPrice: 199,
      annualPriceId: "price_1RkXYtGfTIjQ6vqfdwYSv3Xf",
      annuallyPrice: 1908
    },
    createdAt: new Date()
  },
  {
    productName: "Growth",
    stripeProductId: "prod_SfmYiuCBucOk8B",
    price: {
      monthlyPriceId: "price_1RkQz0GfTIjQ6vqfW8cLL5fA",
      monthlyPrice: 99,
      annualPriceId: "price_1RkXXvGfTIjQ6vqfyTqDTqEf",
      annuallyPrice: 948
    },
    createdAt: new Date()
  },
  {
    productName: "Starter",
    stripeProductId: "prod_SfmVTIzOCesVjX",
    price: {
      monthlyPriceId: "price_1RkQwhGfTIjQ6vqf2Uqd7Vjh",
      monthlyPrice: 49,
      annualPriceId: "price_1RkXUVGfTIjQ6vqfTBeoCyCo",
      annuallyPrice: 468
    },
    createdAt: new Date()
  }
]);
