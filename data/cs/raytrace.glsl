#version 430
layout(local_size_x = 1, local_size_y = 1) in;

uniform vec3[4] cameraParams; // [center, pixel00, deltaU, deltaV]
uniform writeonly image2D outputImg;
uniform int numSpheres;
uniform int NumRaysPerPixel;
uniform int frameNumber;

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

struct Light {
  vec3 position;
  vec3 color;
};

layout(std430, binding=1) buffer spheresBuffer {
  Sphere spheres[];
};

layout(std430, binding=2) buffer lightsBuffer {
    Light lights[];
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
                ray.invDir = nextDir * -1.0;
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

void main() {
    ivec2 coords = ivec2(gl_GlobalInvocationID.xy);
    uint pixelIndex = coords.y * gl_NumWorkGroups.x + coords.x;

    vec3 cameraCenter = cameraParams[0];
    vec3 pixel00 = cameraParams[1];
    vec3 deltaU = cameraParams[2];
    vec3 deltaV = cameraParams[3];

    vec3 pixelCenter = pixel00 + (coords.x * deltaU) + (coords.y * deltaV);
    vec3 direction = normalize(pixelCenter - cameraCenter);

    Ray ray = Ray(cameraCenter, direction, direction * -1.0, vec3(1), 0);

    uint rngState = pixelIndex + frameNumber * 719393;

    vec3 color = vec3(0);
    for (int rayIndex = 0; rayIndex < NumRaysPerPixel; rayIndex++) {
        color += Trace(ray, rngState);
    }

    color /= NumRaysPerPixel;

    imageStore(outputImg, coords, vec4(color.xyz, 1.0));
}