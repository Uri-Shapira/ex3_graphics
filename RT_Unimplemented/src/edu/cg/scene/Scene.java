package edu.cg.scene;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import edu.cg.Logger;
import edu.cg.algebra.Ops;
import edu.cg.algebra.Point;
import edu.cg.algebra.Ray;
import edu.cg.algebra.Hit;
import edu.cg.algebra.Vec;
import edu.cg.scene.camera.PinholeCamera;
import edu.cg.scene.lightSources.Light;
import edu.cg.scene.objects.Surface;
import edu.cg.scene.objects.Intersectable;

public class Scene {
	private String name = "scene";
	private int maxRecursionLevel = 1;
	private int antiAliasingFactor = 1; //gets the values of 1, 2 and 3
	private boolean renderRefarctions = false;
	private boolean renderReflections = false;
	
	private PinholeCamera camera;
	private Vec ambient = new Vec(1, 1, 1); //white
	private Vec backgroundColor = new Vec(0, 0.5, 1); //blue sky
	private List<Light> lightSources = new LinkedList<>();
	private List<Surface> surfaces = new LinkedList<>();
	private transient double hitPointEscalationIndex = 0.000001;
	
	
	//MARK: initializers
	public Scene initCamera(Point eyePoistion, Vec towardsVec, Vec upVec,  double distanceToPlain) {
		this.camera = new PinholeCamera(eyePoistion, towardsVec, upVec,  distanceToPlain);
		return this;
	}
	
	public Scene initAmbient(Vec ambient) {
		this.ambient = ambient;
		return this;
	}
	
	public Scene initBackgroundColor(Vec backgroundColor) {
		this.backgroundColor = backgroundColor;
		return this;
	}
	
	public Scene addLightSource(Light lightSource) {
		lightSources.add(lightSource);
		return this;
	}
	
	public Scene addSurface(Surface surface) {
		surfaces.add(surface);
		return this;
	}
	
	public Scene initMaxRecursionLevel(int maxRecursionLevel) {
		this.maxRecursionLevel = maxRecursionLevel;
		return this;
	}
	
	public Scene initAntiAliasingFactor(int antiAliasingFactor) {
		this.antiAliasingFactor = antiAliasingFactor;
		return this;
	}
	
	public Scene initName(String name) {
		this.name = name;
		return this;
	}
	
	public Scene initRenderRefarctions(boolean renderRefarctions) {
		this.renderRefarctions = renderRefarctions;
		return this;
	}
	
	public Scene initRenderReflections(boolean renderReflections) {
		this.renderReflections = renderReflections;
		return this;
	}
	
	//MARK: getters
	public String getName() {
		return name;
	}
	
	public int getFactor() {
		return antiAliasingFactor;
	}
	
	public int getMaxRecursionLevel() {
		return maxRecursionLevel;
	}
	
	public boolean getRenderRefarctions() {
		return renderRefarctions;
	}
	
	public boolean getRenderReflections() {
		return renderReflections;
	}
	
	@Override
	public String toString() {
		String endl = System.lineSeparator(); 
		return "Camera: " + camera + endl +
				"Ambient: " + ambient + endl +
				"Background Color: " + backgroundColor + endl +
				"Max recursion level: " + maxRecursionLevel + endl +
				"Anti aliasing factor: " + antiAliasingFactor + endl +
				"Light sources:" + endl + lightSources + endl +
				"Surfaces:" + endl + surfaces;
	}
	
	private transient ExecutorService executor = null;
	private transient Logger logger = null;
	
	private void initSomeFields(int imgWidth, int imgHeight, Logger logger) {
		this.logger = logger;
		//TODO: initialize your additional field here.
		//      You can also change the method signature if needed.
	}

	public BufferedImage render(int imgWidth, int imgHeight, double viewPlainWidth,Logger logger)
			throws InterruptedException, ExecutionException {
		// TODO: Please notice the following comment.
		// This method is invoked each time Render Scene button is invoked.
		// Use it to initialize additional fields you need.
		initSomeFields(imgWidth, imgHeight, logger);
		
		BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
		camera.initResolution(imgHeight, imgWidth, viewPlainWidth);
		int nThreads = Runtime.getRuntime().availableProcessors();
		nThreads = nThreads < 2 ? 2 : nThreads;
		this.logger.log("Intitialize executor. Using " + nThreads + " threads to render " + name);
		executor = Executors.newFixedThreadPool(nThreads);
		
		@SuppressWarnings("unchecked")
		Future<Color>[][] futures = (Future<Color>[][])(new Future[imgHeight][imgWidth]);
		
		this.logger.log("Starting to shoot " +
			(imgHeight*imgWidth*antiAliasingFactor*antiAliasingFactor) +
			" rays over " + name);

		for(int y = 0; y < imgHeight; ++y)
			for(int x = 0; x < imgWidth; ++x)
				futures[y][x] = calcColor(x, y);
		
		this.logger.log("Done shooting rays.");
		this.logger.log("Wating for results...");
		
		for(int y = 0; y < imgHeight; ++y)
			for(int x = 0; x < imgWidth; ++x) {
				Color color = futures[y][x].get();
				img.setRGB(x, y, color.getRGB());
			}
		
		executor.shutdown();
		
		this.logger.log("Ray tracing of " + name + " has been completed.");
		
		executor = null;
		this.logger = null;
		
		return img;
	}
	
	private Future<Color> calcColor(int x, int y) {
		return executor.submit(() -> {
			if(antiAliasingFactor > 1){
				Vec totalColor = new Vec(0.0);
				for(int i = 0; i < antiAliasingFactor; i++){
					for(int j = 0; j < antiAliasingFactor; j++){
						Point pixelPortion = anti_aliasing_transform(x,y,i,j);
						Ray ray = new Ray(camera.getCameraPosition(), pixelPortion);
						totalColor = totalColor.add(calcColor(ray, 0));
					}
				}
				Vec averagedColor = totalColor.mult(1.0/Math.pow(antiAliasingFactor,2));
				return averagedColor.toColor();
			}
			else{
				Point centerPoint = camera.transform(x, y);
				Ray ray = new Ray(camera.getCameraPosition(), centerPoint);
				Vec color = calcColor(ray, 0);
				return color.toColor();
			}
		});
	}

	private Point anti_aliasing_transform(int x, int y, int i, int j){
		double pixelWidth = camera.viewPlainWidth / camera.resolutionX;
		double pixelHeight = camera.viewPlainWidth / camera.resolutionY;
		Vec right = camera.rightVec.mult((x - (int)(camera.resolutionX/2.0) + (double)i/antiAliasingFactor) * pixelWidth);
		Vec up = camera.upVec.mult(-1.0 * (y - (int)(camera.resolutionY/2.0) + (double)j/antiAliasingFactor) * pixelHeight);
		return camera.imageMiddle.add(right).add(up);
	}

	private Vec calcColor(Ray ray, int recursionLevel) {
		Hit closest_hit = closestHit(ray);
		if(closest_hit != null){
			Surface surface_hit = closest_hit.getSurface();
			Vec Ka = surface_hit.Ka();
			Vec Kd = surface_hit.Kd();
			Vec Ks = surface_hit.Ks();
			Vec colorVector = Ka.mult(this.ambient);
			for (Light lightSource : this.lightSources){
				if(!lightIsOccluded(lightSource.rayToLight(ray.getHittingPoint(closest_hit)), lightSource)){
					Vec diffuse = getDiffuse(Kd, closest_hit, lightSource, ray);
					Vec specular = getSpecular(Ks, closest_hit, lightSource, ray, surface_hit.shininess());
					colorVector = colorVector.add(diffuse).add(specular);
				}
			}
			recursionLevel++;
			if (recursionLevel >= maxRecursionLevel) {
				return colorVector;
			}
			if(renderReflections){
				double Kr = surface_hit.reflectionIntensity();
				Vec reflected = Ops.reflect(ray.direction(), closest_hit.getNormalToSurface());
				Ray reflectionRay = new Ray(ray.getHittingPoint(closest_hit).add(ray.direction().mult(hitPointEscalationIndex)), reflected.normalize());
				colorVector = colorVector.add(calcColor(reflectionRay, recursionLevel).mult(Kr));
			}
			// TODO: add refraction index
			if(renderRefarctions && surface_hit.isTransparent()){
				double Kt = surface_hit.refractionIntensity();
				double n1 = surface_hit.n1(closest_hit);
				double n2 = surface_hit.n2(closest_hit);
				Vec refracted = Ops.refract(ray.direction(), closest_hit.getNormalToSurface(), n1, n2);
				Ray refractedRay = new Ray(ray.getHittingPoint(closest_hit).add(ray.direction().mult(hitPointEscalationIndex)), refracted.normalize());
				colorVector = colorVector.add(calcColor(refractedRay, recursionLevel).mult(Kt));
			}
			return colorVector;
		}
		return this.backgroundColor;
	}

	private Vec getDiffuse(Vec Kd, Hit hit, Light lightSource, Ray ray){
		Point hitPoint = ray.getHittingPoint(hit);
		Ray rayToLight = lightSource.rayToLight(hitPoint);
		Vec intensity = lightSource.intensity(hitPoint, rayToLight);
		Vec normalToSurface = hit.getNormalToSurface();
		Vec diffuse = Kd.mult(intensity).mult(normalToSurface.dot(rayToLight.direction()));
		return diffuse;
	}

	private Vec getSpecular(Vec Ks, Hit hit, Light lightSource, Ray ray, int n){
		Point hitPoint = ray.getHittingPoint(hit);
		Ray rayToLight = lightSource.rayToLight(hitPoint);
		Vec intensity = lightSource.intensity(hitPoint, rayToLight);
		Vec V = ray.direction().neg().normalize();
		Vec R_hat = Ops.reflect(rayToLight.direction(), hit.getNormalToSurface()).normalize();
		double cosine_alpha = V.dot(R_hat);
		Vec specular = Ks.mult(intensity).mult(Math.pow(cosine_alpha, n));
		return specular;
	}

	private Hit closestHit(Ray ray){
		double closestT = Ops.infinity;
		Hit closestHit = null;
		for (Intersectable surface : surfaces){
			Hit hit = surface.intersect(ray);
			if (hit != null) {
				if (hit.t() < closestT ){
					closestHit = hit;
					closestT = closestHit.t();
				}
			}
		}
		return closestHit;
	}

	private boolean lightIsOccluded(Ray rayToLight, Light light){
		for(Surface surface : surfaces){
			if (light.isOccludedBy(surface, rayToLight)){
				return true;
			}
		}
		return false;
	}

}
