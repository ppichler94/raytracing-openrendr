#version 430
layout(local_size_x = 1, local_size_y = 1) in;

uniform writeonly image2D outputImg;
uniform int numSpheres;
uniform int NumRaysPerPixel;
uniform int frameNumber;

uniform vec3 sensorOrigin;      // so - sensor origin
uniform vec3 sensorDirection;   // sd - sensor view direction (normal to sensor plane)
uniform float sensorWidth;     // sw - sensor width
uniform float sensorHeight;    // sh - sensor height
uniform float lensDiameter;    // sl - lens diameter
uniform float focalLength;     // f - focal length in m
uniform float fStop;           // f-stop number
uniform float sensorDistance; // SI - distance from lens to sensor
uniform vec3 sensorU;          // su - orthogonal axis spanning sensor plane
uniform vec3 sensorV;          // sv - orthogonal axis spanning sensor plane

struct Material {
    vec3 color;
    vec3 emissionColor;
    float emissionStrength;
};

struct Sphere {
    vec3 position;
    float radius;
    Material material;
};

layout(std430, binding=1) buffer spheresBuffer {
  Sphere spheres[];
};

struct Ray {
  vec3 pos;
  vec3 dir;
  vec3 invDir;
  vec3 transmittance;
  int bounceCount;
};

struct HitInfo {
    bool didHit;
    float distance;
    vec3 hitPoint;
    vec3 normal;
    Material material;
};

float RandomValue(inout uint state) {
    state = state * 747796405 + 2891336453;
    uint result = ((state >> ((state >> 28) + 4)) ^ state) * 277803737;
    result = (result >> 22) ^ result;
    return result / 4294967295.0;
}

float RandomValueNormalDistribution(inout uint state) {
    float theta = 2 * 3.1415926 * RandomValue(state);
    float rho = sqrt(-2 * log(RandomValue(state)));
    return rho * cos(theta);
}

vec3 RandomDirection(inout uint state) {
    float x = RandomValueNormalDistribution(state) * 2 - 1;
    float y = RandomValueNormalDistribution(state) * 2 - 1;
    float z = RandomValueNormalDistribution(state) * 2 - 1;
    return normalize(vec3(x, y, z));
}

vec3 RandomHemisphereDirection(vec3 normal, inout uint state) {
    vec3 dir = RandomDirection(state);
    return dir * sign(dot(normal, dir));
}

HitInfo RaySphere(Ray ray, vec3 sphereCenter, float sphereRadius) {
    HitInfo hitInfo;
    hitInfo.didHit = false;
    vec3 offsetRayOrigin = ray.pos - sphereCenter;

    float a = dot(ray.dir, ray.dir);
    float b = 2.0 * dot(offsetRayOrigin, ray.dir);
    float c = dot(offsetRayOrigin, offsetRayOrigin) - sphereRadius * sphereRadius;
    float discriminant = b * b - 4.0 * a * c;

    if (discriminant >= 0) {
        float dst = (-b - sqrt(discriminant)) / (2.0 * a);

        if (dst >= 0) {
            hitInfo.didHit = true;
            hitInfo.distance = dst;
            hitInfo.hitPoint = ray.pos + ray.dir * dst;
            hitInfo.normal = normalize(hitInfo.hitPoint - sphereCenter);
        }
    }

    return hitInfo;
}

HitInfo CalculateRayCollision(Ray ray) {
    HitInfo closestHit;
    closestHit.didHit = false;
    closestHit.distance = uintBitsToFloat(0x7F800000); //infinity

    for (int i = 0; i < numSpheres; i++) {
        Sphere sphere = spheres[i];
        HitInfo info = RaySphere(ray, sphere.position, sphere.radius);
        if (info.didHit && info.distance < closestHit.distance) {
            closestHit = info;
            closestHit.material = sphere.material;
        }
    }

    return closestHit;
}

vec3 Trace(Ray initialRay, inout uint rngState) {
    vec3 totalLight = vec3(0.0);

    const int StackCapacity = 16;
    const int MaxBounceCount = 9;
    int stackCount = 0;
    Ray stack[StackCapacity];
    stack[stackCount++] = initialRay;

    while (stackCount > 0) {
        Ray ray = stack[--stackCount];
        for (int i = ray.bounceCount; i <= MaxBounceCount; i++) {
            HitInfo info = CalculateRayCollision(ray);
            if (info.didHit) {
                vec3 nextDir = normalize(info.normal + RandomDirection(rngState));
                ray.pos = info.hitPoint;
                ray.dir = nextDir;
                ray.invDir = -nextDir;
                ray.bounceCount += 1;

                totalLight += ray.transmittance * info.material.emissionColor * info.material.emissionStrength;
                ray.transmittance *= info.material.color;
            }
            else {
                break;
            }
        }
    }

    return totalLight;
}

Ray GenerateCameraRay(ivec2 coords, ivec2 subpixel, inout uint rngState) {
    ivec2 resolution = ivec2(gl_NumWorkGroups.xy);

    // Calculate camera parameters based on lens simulation
    float SO = focalLength * sensorDistance / (sensorDistance - focalLength);
    float D = focalLength / fStop;
    D = D / lensDiameter; // normalized aperture radius [0, 1]
    D = min(D, 1.0);

    vec3 lensCenter = sensorOrigin + sensorDirection * sensorDistance;

    // Calculate sensor position with subpixel sampling
    float sx = ((coords.x + 0.5 * (0.5 + subpixel.x)) / resolution.x - 0.5) * sensorWidth;
    float sy = ((coords.y + 0.5 * (0.5 + subpixel.y)) / resolution.y - 0.5) * sensorHeight;
    vec3 sensorPos = sensorOrigin + sensorU * sx + sensorV * sy;

    // Ray through center of the lens
    vec3 lcRayDir = normalize((sensorOrigin + sensorDirection * sensorDistance) - sensorPos);

    // Image plane parallel to sensor
    vec3 imagePlaneOrigin = sensorOrigin + sensorDirection * SO;
    vec3 imagePlaneNormal = sensorDirection;

    // Find intersection with image plane
    float denom = dot(lcRayDir, imagePlaneNormal);
    float t = dot(imagePlaneOrigin - sensorPos, imagePlaneNormal) / denom;

    if (t < 1e-6) {
        // Fallback to simple ray if no intersection
        return Ray(sensorPos, lcRayDir, -lcRayDir, vec3(1), 0);
    }

    vec3 pointOnImagePlane = sensorPos + lcRayDir * t;

    // Sample lens aperture
    float phi = RandomValue(rngState) * 2.0 * 3.1415926;
    float rad = RandomValue(rngState) * D;

    float lx = 0.5 * rad * cos(phi) * lensDiameter;
    float ly = 0.5 * rad * sin(phi) * lensDiameter;

    vec3 lensPos = lensCenter + sensorU * lx + sensorV * ly;
    vec3 rayDir = normalize(pointOnImagePlane - lensPos);

    return Ray(lensPos, rayDir, -rayDir, vec3(1), 0);
}

void main() {
    ivec2 coords = ivec2(gl_GlobalInvocationID.xy);
    uint pixelIndex = coords.y * gl_NumWorkGroups.x + coords.x;
    uint rngState = pixelIndex + frameNumber * 719393;

    vec3 color = vec3(0);
    // 2x2 subpixel sampling
    for (int ysub = 0; ysub < 2; ysub++) {
        for (int xsub = 0; xsub < 2; xsub++) {
            ivec2 subpixel = ivec2(xsub, ysub);

            for (int rayIndex = 0; rayIndex < NumRaysPerPixel; rayIndex++) {
                Ray ray = GenerateCameraRay(coords, subpixel, rngState);
                color += Trace(ray, rngState);
            }
        }
    }

    color /= (NumRaysPerPixel * 4); // Divide by 4 for 2x2 subpixel sampling

    coords.y = int(gl_NumWorkGroups.y) - 1 - coords.y;
    imageStore(outputImg, coords, vec4(color.xyz, 1.0));
}